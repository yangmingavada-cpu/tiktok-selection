package com.tiktok.selection.engine;

import com.tiktok.selection.common.SessionStatusEnum;
import com.tiktok.selection.common.SnapshotTypeEnum;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.dto.response.SseProgressEvent;
import com.tiktok.selection.entity.LlmConfig;
import com.tiktok.selection.entity.Session;
import com.tiktok.selection.entity.SessionData;
import com.tiktok.selection.entity.SessionStep;
import com.tiktok.selection.entity.SessionStepSnapshot;
import com.tiktok.selection.manager.SseEmitterManager;
import com.tiktok.selection.mapper.SessionDataMapper;
import com.tiktok.selection.mapper.SessionMapper;
import com.tiktok.selection.mapper.SessionStepMapper;
import com.tiktok.selection.mapper.SessionStepSnapshotMapper;
import com.tiktok.selection.service.EchotikApiKeyService;
import com.tiktok.selection.service.IntentService;
import com.tiktok.selection.service.LlmConfigService;
import com.tiktok.selection.service.QuotaService;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Block编排器，负责按Block链顺序执行各Block并管理数据流转。
 * 支持暂停（SS01）、恢复和取消。
 *
 * @author system
 * @date 2026/03/22
 */
@Component
public class BlockOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BlockOrchestrator.class);

    /** 快照中保留完整数据的最大条目数 */
    private static final int SNAPSHOT_FULL_DATA_THRESHOLD = 200;

    /** SS01 返回的暂停状态标识 */
    private static final String PAUSED_STATUS = "paused";

    /** block_chain 最大积木数，防止恶意构造超长链导致OOM */
    private static final int MAX_BLOCK_CHAIN_SIZE = 50;

    /**
     * 调用外部LLM的Block ID集合（规约第11条：对这些Block额外执行防刷检查）
     */
    private static final Set<String> LLM_BLOCK_IDS = Set.of("SCORE_SEMANTIC", "ANNOTATE_LLM_COMMENT");

    private final BlockExecutorRegistry blockExecutorRegistry;
    private final SseEmitterManager sseEmitterManager;
    private final SessionMapper sessionMapper;
    private final SessionStepMapper sessionStepMapper;
    private final SessionStepSnapshotMapper sessionStepSnapshotMapper;
    private final SessionDataMapper sessionDataMapper;
    private final QuotaService quotaService;
    private final EchotikApiKeyService echotikApiKeyService;
    private final IntentService intentService;
    private final LlmConfigService llmConfigService;

    /** 执行阶段累计 LLM token 预算，覆盖语义评分和 AI 评语等执行型能力 */
    @Value("${ai.budget.session-execute-token-quota:120000}")
    private long sessionExecuteLlmTokenQuota;

    public BlockOrchestrator(BlockExecutorRegistry blockExecutorRegistry,
                             SseEmitterManager sseEmitterManager,
                             SessionMapper sessionMapper,
                             SessionStepMapper sessionStepMapper,
                             SessionStepSnapshotMapper sessionStepSnapshotMapper,
                             SessionDataMapper sessionDataMapper,
                             QuotaService quotaService,
                             EchotikApiKeyService echotikApiKeyService,
                             @Lazy IntentService intentService,
                             LlmConfigService llmConfigService) {
        this.blockExecutorRegistry = blockExecutorRegistry;
        this.sseEmitterManager     = sseEmitterManager;
        this.sessionMapper         = sessionMapper;
        this.sessionStepMapper     = sessionStepMapper;
        this.sessionStepSnapshotMapper = sessionStepSnapshotMapper;
        this.sessionDataMapper     = sessionDataMapper;
        this.quotaService          = quotaService;
        this.echotikApiKeyService  = echotikApiKeyService;
        this.intentService         = intentService;
        this.llmConfigService      = llmConfigService;
    }

    // ─── 公开异步入口 ────────────────────────────────────────────────────────────

    /**
     * 从第一步开始异步执行会话的Block链
     */
    @Async("blockExecutorPool")
    public void executeAsync(Session session) {
        String sessionId = session.getId();

        // 规约第1条：异步线程中重新校验 session 归属和状态
        Session freshSession = sessionMapper.selectById(sessionId);
        if (freshSession == null || !freshSession.getUserId().equals(session.getUserId())) {
            log.error("Session ownership verification failed in async context: sessionId={}", sessionId);
            sseEmitterManager.sendEvent(sessionId,
                    SseProgressEvent.fail(sessionId, 0, null, "执行失败，请稍后重试"));
            sseEmitterManager.complete(sessionId);
            return;
        }
        if (SessionStatusEnum.CANCELLED.getValue().equals(freshSession.getStatus())) {
            log.warn("Session was cancelled before async execution started: sessionId={}", sessionId);
            // 取消场景：SessionService.cancelSession() 已发过 SSE fail，此处无需重复
            return;
        }

        List<Map<String, Object>> blockChain = toBlockChain(freshSession.getBlockChain());
        if (blockChain.size() > MAX_BLOCK_CHAIN_SIZE) {
            log.error("block_chain size {} exceeds limit {}, sessionId={}", blockChain.size(), MAX_BLOCK_CHAIN_SIZE, sessionId);
            failSession(freshSession, 0, 0L);
            sseEmitterManager.sendEvent(sessionId,
                    SseProgressEvent.fail(sessionId, 0, null, "执行配置超出限制，请联系管理员"));
            sseEmitterManager.complete(sessionId);
            return;
        }

        runLoop(freshSession, blockChain, 0,
                new LoopState(new ArrayList<>(), new ArrayList<>(), null, 0, 0L));
    }

    /**
     * 从暂停点之后继续执行
     */
    @Async("blockExecutorPool")
    public void resumeAsync(Session session) {
        String sessionId = session.getId();

        Session freshSession = sessionMapper.selectById(sessionId);
        if (freshSession == null || !freshSession.getUserId().equals(session.getUserId())) {
            log.error("Resume: session ownership verification failed: sessionId={}", sessionId);
            sseEmitterManager.sendEvent(sessionId,
                    SseProgressEvent.fail(sessionId, 0, null, "执行失败，请稍后重试"));
            sseEmitterManager.complete(sessionId);
            return;
        }

        List<Map<String, Object>> blockChain = toBlockChain(freshSession.getBlockChain());
        if (blockChain.size() > MAX_BLOCK_CHAIN_SIZE) {
            log.error("block_chain size {} exceeds limit {}, sessionId={}", blockChain.size(), MAX_BLOCK_CHAIN_SIZE, sessionId);
            failSession(freshSession, 0, 0L);
            sseEmitterManager.sendEvent(sessionId,
                    SseProgressEvent.fail(sessionId, 0, null, "执行配置超出限制，请联系管理员"));
            sseEmitterManager.complete(sessionId);
            return;
        }
        int startIndex = freshSession.getCurrentStep();

        SessionData sd = sessionDataMapper.selectById(sessionId);
        LoopState state = restoreLoopState(freshSession, sd);

        log.info("Resuming session: id={}, startIndex={}", sessionId, startIndex);
        runLoop(freshSession, blockChain, startIndex, state);
    }

    // ─── 核心执行循环 ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void runLoop(Session session,
                         List<Map<String, Object>> blockChain,
                         int startIndex,
                         LoopState state) {

        String sessionId  = session.getId();
        int    totalSteps = blockChain.size();

        try {
            for (int i = startIndex; i < totalSteps; i++) {
                int seq = i + 1;
                Map<String, Object> blockDef = blockChain.get(i);
                String blockId = (String) blockDef.get("blockId");
                Map<String, Object> config = blockDef.get("config") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : HashMap.newHashMap(4);

                // 取消检查：每块执行前从 DB 确认 session 未被取消
                if (isCancelled(sessionId)) {
                    log.info("Session {} was cancelled before step {}, stopping", sessionId, seq);
                    return;
                }

                sendStepStart(sessionId, seq, totalSteps, blockId);
                quotaService.checkQuota(session.getUserId(), 1);
                if (LLM_BLOCK_IDS.contains(blockId)) {
                    quotaService.checkLlmRateLimit(session.getUserId());
                    ensureExecutionTokenBudget(sessionId, state.totalTokens, blockId, seq);
                }

                BlockResult result = executeBlock(session, seq, blockId, config,
                        state.inputData, state.availableFields, state.currentOutputType);

                SessionStep step = saveStep(sessionId, seq, blockId, config, result);
                saveSnapshot(step.getId(), sessionId, result);
                updateSessionProgress(session, seq);

                // 暂停检测（SS01 设置 status="paused"）
                if (PAUSED_STATUS.equals(result.getStatus())) {
                    handlePause(session, seq, blockId, result, state);
                    return;
                }

                applyStepResult(state, result);
                if (LLM_BLOCK_IDS.contains(blockId)) {
                    ensureExecutionTokenBudget(sessionId, state.totalTokens, blockId, seq);
                }

                // 每步更新 currentView（持久化中间结果，前端刷新可看到）
                List<Map<String, Object>> stepDims = buildDims(state.availableFields);
                updateCurrentView(sessionId, state.inputData, stepDims, state.currentOutputType);
                sendStepComplete(sessionId, seq, totalSteps, blockId, result, stepDims);
            }

            // 正常完成
            List<Map<String, Object>> finalDims = buildDims(state.availableFields);
            completeSession(session, state.totalApiCalls, state.totalTokens, state.inputData, finalDims);
            sseEmitterManager.sendEvent(sessionId,
                    SseProgressEvent.sessionComplete(sessionId, totalSteps));
            sseEmitterManager.complete(sessionId);

            // 执行后并行触发：蒸馏记忆 + 竞品洞察（fire-and-forget，不阻塞 SSE 完成）
            triggerPostExecutionPipeline(session, state);

        } catch (Exception e) {
            failSession(session, state.totalApiCalls, state.totalTokens);
            String safeMsg = (e instanceof com.tiktok.selection.common.BusinessException be)
                    ? be.getMessage() : "执行失败，请稍后重试";
            sseEmitterManager.sendEvent(sessionId,
                    SseProgressEvent.fail(sessionId, session.getCurrentStep(), null, safeMsg));
            sseEmitterManager.complete(sessionId);
            log.error("Session execution failed: sessionId={}", sessionId, e);
        }
    }

    /**
     * 处理积木链暂停（SS01触发），保存会话状态并推送SSE事件。
     */
    private void handlePause(Session session, int seq, String blockId,
                              BlockResult result, LoopState state) {
        List<Map<String, Object>> pauseData   = result.getOutputData();
        List<String>              pauseFields = result.getOutputFields() != null
                ? result.getOutputFields() : state.availableFields;
        String pauseType = result.getOutputType() != null
                ? result.getOutputType() : state.currentOutputType;
        List<Map<String, Object>> dims = buildDims(pauseFields);
        pauseSession(session, state.totalApiCalls, state.totalTokens, pauseData, dims, pauseType);
        sseEmitterManager.sendEvent(session.getId(),
                SseProgressEvent.sessionPaused(session.getId(), seq, blockId,
                        result.getErrorMessage(), pauseData, dims));
    }

    /**
     * 将单步执行结果应用到循环状态（更新数据流、字段信息、调用统计及API密钥余量）。
     */
    private void applyStepResult(LoopState state, BlockResult result) {
        state.inputData         = result.getOutputData()   != null ? result.getOutputData()   : new ArrayList<>();
        state.availableFields   = result.getOutputFields() != null ? result.getOutputFields() : state.availableFields;
        state.currentOutputType = result.getOutputType()   != null ? result.getOutputType()   : state.currentOutputType;
        int stepApiCalls = result.getEchotikApiCalls() != null ? result.getEchotikApiCalls() : 0;
        state.totalApiCalls += stepApiCalls;
        state.totalTokens   += result.getLlmTokensUsed() != null ? result.getLlmTokensUsed() : 0;
        // 原子扣减密钥余量（每个 Block 执行完后立即扣减，确保看板数据实时）
        if (stepApiCalls > 0 && result.getEchotikKeyId() != null) {
            echotikApiKeyService.decrementRemainingCalls(result.getEchotikKeyId(), stepApiCalls);
        }
    }

    // ─── 私有辅助方法 ─────────────────────────────────────────────────────────────

    private BlockResult executeBlock(Session session, int seq, String blockId,
                                     Map<String, Object> config,
                                     List<Map<String, Object>> inputData,
                                     List<String> availableFields,
                                     String currentOutputType) throws Exception {
        BlockExecutor executor = blockExecutorRegistry.getExecutor(blockId);

        BlockContext context = new BlockContext();
        context.setSessionId(session.getId());
        context.setUserId(session.getUserId());
        context.setSeq(seq);
        context.setBlockId(blockId);
        context.setBlockConfig(config);
        context.setInputData(inputData);
        context.setAvailableFields(availableFields);
        context.setCurrentOutputType(currentOutputType);

        // 注入 Echotik API 凭证（DS/TR/EN 类 Block 调用 Echotik 时需要）
        echotikApiKeyService.getAvailableKey().ifPresent(key -> {
            context.setEchotikKeyId(key.getId());
            context.setEchotikApiKey(echotikApiKeyService.decryptApiKey(key));
            context.setEchotikApiSecret(echotikApiKeyService.decryptApiSecret(key));
        });

        // 注入 LLM 配置（SC02/LA01 类 Block 调用 Python LLM 端点时需要）
        if (LLM_BLOCK_IDS.contains(blockId)) {
            try {
                LlmConfig llmCfg = llmConfigService.getActiveLlmConfig();
                Map<String, Object> llmConfigMap = llmConfigService.toLlmConfigMap(llmCfg);
                log.info("[DEBUG-LLM] BEFORE put: config.llm_config={}", config.get("llm_config"));
                log.info("[DEBUG-LLM] injecting: model={}, base_url={}, api_key_len={}",
                    llmConfigMap.get("model"), llmConfigMap.get("base_url"),
                    llmConfigMap.get("api_key") != null ? ((String)llmConfigMap.get("api_key")).length() : 0);
                config.put("llm_config", llmConfigMap);
                log.info("[DEBUG-LLM] AFTER put: config.llm_config={}", config.get("llm_config") != null ? "SET(keys=" + ((Map<?,?>)config.get("llm_config")).keySet() + ")" : "NULL");
            } catch (Exception e) {
                log.warn("Failed to inject llm_config for block {}: {}", blockId, e.getMessage(), e);
            }
        }

        return executor.execute(context);
    }

    private SessionStep saveStep(String sessionId, int seq, String blockId,
                                 Map<String, Object> config, BlockResult result) {
        SessionStep step = new SessionStep();
        step.setSessionId(sessionId);
        step.setSeq(seq);
        step.setBlockId(blockId);
        step.setBlockConfig(config);
        step.setLabel(blockId + " - Step " + seq);
        step.setInputCount(result.getInputCount());
        step.setOutputCount(result.getOutputCount());
        step.setEchotikApiCalls(result.getEchotikApiCalls());
        step.setLlmTotalTokens(result.getLlmTokensUsed());
        step.setEchotikKeyId(result.getEchotikKeyId());
        step.setDurationMs(result.getDurationMs() != null ? result.getDurationMs().intValue() : 0);
        step.setStatus(result.getStatus());
        step.setErrorMessage(PAUSED_STATUS.equals(result.getStatus()) ? null : result.getErrorMessage());
        step.setCreateTime(LocalDateTime.now());
        sessionStepMapper.insert(step);
        return step;
    }

    private void saveSnapshot(Long stepId, String sessionId, BlockResult result) {
        SessionStepSnapshot snapshot = new SessionStepSnapshot();
        snapshot.setStepId(stepId);
        snapshot.setSessionId(sessionId);
        snapshot.setCreateTime(LocalDateTime.now());

        List<Map<String, Object>> outputData = result.getOutputData();
        if (outputData != null && outputData.size() <= SNAPSHOT_FULL_DATA_THRESHOLD) {
            snapshot.setSnapshotType(SnapshotTypeEnum.FULL_DATA.getValue());
            snapshot.setSnapshotData(outputData);
        } else {
            snapshot.setSnapshotType(SnapshotTypeEnum.IDS_ONLY.getValue());
            snapshot.setSnapshotIds(extractSnapshotIds(outputData));
        }
        sessionStepSnapshotMapper.insert(snapshot);
    }

    private static List<Object> extractSnapshotIds(List<Map<String, Object>> data) {
        List<Object> ids = new ArrayList<>();
        if (data == null) return ids;
        for (Map<String, Object> item : data) {
            Object id = item.get("id");
            if (id == null) id = item.get("product_id");
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private void updateSessionProgress(Session session, int seq) {
        session.setCurrentStep(seq);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private void updateCurrentView(String sessionId,
                                    List<Map<String, Object>> data,
                                    List<Map<String, Object>> dims,
                                    String outputType) {
        SessionData sd = sessionDataMapper.selectById(sessionId);
        if (sd == null) return;
        Map<String, Object> cv = HashMap.newHashMap(8);
        cv.put("data",       data);
        cv.put("dims",       dims);
        cv.put("totalCount", data != null ? data.size() : 0);
        if (outputType != null) cv.put("outputType", outputType);
        sd.setCurrentView(cv);
        sd.setUpdateTime(LocalDateTime.now());
        sessionDataMapper.updateById(sd);
    }

    private void pauseSession(Session session, int totalApiCalls, long totalTokens,
                               List<Map<String, Object>> data,
                               List<Map<String, Object>> dims, String outputType) {
        session.setStatus(SessionStatusEnum.PAUSED.getValue());
        session.setEchotikApiCalls(totalApiCalls);
        session.setLlmTotalTokens(totalTokens);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        updateCurrentView(session.getId(), data, dims, outputType);
        log.info("Session paused: id={}", session.getId());
    }

    private void completeSession(Session session, int totalApiCalls,
                                  long totalTokens,
                                  List<Map<String, Object>> finalData,
                                  List<Map<String, Object>> finalDims) {
        session.setStatus(SessionStatusEnum.COMPLETED.getValue());
        session.setCompleteTime(LocalDateTime.now());
        session.setEchotikApiCalls(totalApiCalls);
        session.setLlmTotalTokens(totalTokens);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);

        updateCurrentView(session.getId(), finalData, finalDims, null);
        log.info("Session completed: id={}, apiCalls={}, tokens={}",
                session.getId(), totalApiCalls, totalTokens);
    }

    private void failSession(Session session, int totalApiCalls, long totalTokens) {
        session.setStatus(SessionStatusEnum.FAILED.getValue());
        session.setEchotikApiCalls(totalApiCalls);
        session.setLlmTotalTokens(totalTokens);
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    private void ensureExecutionTokenBudget(String sessionId, long usedTokens, String blockId, int seq) {
        if (sessionExecuteLlmTokenQuota <= 0 || usedTokens < sessionExecuteLlmTokenQuota) {
            return;
        }
        log.warn("Execution LLM token quota exceeded: sessionId={}, step={}, blockId={}, used={}, quota={}",
                sessionId, seq, blockId, usedTokens, sessionExecuteLlmTokenQuota);
        throw new BusinessException(
                ErrorCode.INVALID_USER_INPUT,
                String.format("执行阶段已达到LLM安全预算（%d/%d），请缩小语义评分或AI评语范围后重试",
                        usedTokens, sessionExecuteLlmTokenQuota)
        );
    }

    private void sendStepStart(String sessionId, int seq, int total, String blockId) {
        sseEmitterManager.sendEvent(sessionId,
                SseProgressEvent.stepStart(sessionId, seq, total, blockId));
    }

    private void sendStepComplete(String sessionId, int seq, int total,
                                   String blockId, BlockResult result,
                                   List<Map<String, Object>> dims) {
        String msg = String.format("完成: %s，输出 %d 条",
                blockId, result.getOutputCount() != null ? result.getOutputCount() : 0);
        sseEmitterManager.sendEvent(sessionId,
                SseProgressEvent.stepComplete(sessionId, seq, total, blockId, msg,
                        result.getOutputData(), dims));
    }

    /** 检查 session 是否已被取消（直接读 DB） */
    private boolean isCancelled(String sessionId) {
        Session s = sessionMapper.selectById(sessionId);
        return s == null || SessionStatusEnum.CANCELLED.getValue().equals(s.getStatus());
    }

    /**
     * 根据字段名列表构建前端 dims 定义，推断基础类型。
     */
    private List<Map<String, Object>> buildDims(List<String> fields) {
        if (fields == null) return new ArrayList<>();
        return fields.stream().map(f -> {
            Map<String, Object> dim = LinkedHashMap.newLinkedHashMap(4);
            dim.put("id",    f);
            dim.put("label", f);
            dim.put("type",  inferFieldType(f));
            return dim;
        }).toList();
    }

    private String inferFieldType(String fieldName) {
        if (fieldName == null) return "string";
        String lower = fieldName.toLowerCase();
        if (lower.contains("score") || lower.contains("rating")) return "score";
        if (lower.contains("rate") || lower.contains("ratio") || lower.contains("growth")
                || lower.contains("percent")) return "percent";
        if (lower.contains("price") || lower.contains("revenue") || lower.contains("sales")
                || lower.contains("volume") || lower.contains("count") || lower.contains("total")
                || lower.contains("num")   || lower.contains("calls") || lower.contains("tokens")) {
            return "number";
        }
        return "string";
    }

    private List<Map<String, Object>> toBlockChain(List<Object> raw) {
        if (raw == null) return new ArrayList<>();
        List<Map<String, Object>> chain = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> m)) {
                throw new com.tiktok.selection.common.BusinessException(
                        com.tiktok.selection.common.ErrorCode.PARAM_ERROR,
                        "block_chain格式非法：元素不是Map对象，实际类型: " + (item == null ? "null" : item.getClass().getSimpleName()));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> block = (Map<String, Object>) m;
            chain.add(block);
        }
        return chain;
    }

    /**
     * 从 currentView 恢复暂停状态，构建 LoopState 供 resumeAsync 使用。
     */
    private LoopState restoreLoopState(Session freshSession, SessionData sd) {
        int  totalApiCalls = freshSession.getEchotikApiCalls() != null ? freshSession.getEchotikApiCalls() : 0;
        long totalTokens   = freshSession.getLlmTotalTokens()  != null ? freshSession.getLlmTotalTokens()  : 0L;

        List<Map<String, Object>> inputData       = new ArrayList<>();
        List<String>              availableFields = new ArrayList<>();
        String                    currentOutputType = null;

        if (sd != null && sd.getCurrentView() != null) {
            Map<String, Object> cv = sd.getCurrentView();
            inputData = parseInputData(cv.get("data"));
            if (cv.get("outputType") instanceof String s) {
                currentOutputType = s;
            }
            availableFields = parseAvailableFields(cv.get("dims"));
        }

        // 兜底：从数据推断字段名
        if (availableFields.isEmpty() && !inputData.isEmpty()) {
            availableFields = new ArrayList<>(inputData.get(0).keySet());
        }

        return new LoopState(inputData, availableFields, currentOutputType, totalApiCalls, totalTokens);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseInputData(Object dataObj) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (dataObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    result.add((Map<String, Object>) m);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseAvailableFields(Object dimsObj) {
        List<String> fields = new ArrayList<>();
        if (dimsObj instanceof List<?> dimList) {
            for (Object dim : dimList) {
                if (dim instanceof Map<?, ?> dimMap) {
                    Object id = ((Map<String, Object>) dimMap).get("id");
                    if (id instanceof String s) fields.add(s);
                }
            }
        }
        return fields;
    }

    /**
     * 执行成功后触发后处理管道（fire-and-forget）：蒸馏记忆 + 竞品洞察。
     * agentThreadId 使用 session.getId() 作为最佳猜测（Python 默认 agentThreadId=sessionId）。
     */
    private void triggerPostExecutionPipeline(Session session, LoopState state) {
        try {
            List<Map<String, Object>> selected = state.inputData != null
                    ? state.inputData
                    : new ArrayList<>();
            // blockChain is stored as List<Object> in session; convert to typed list
            List<Map<String, Object>> blockChain = toBlockChain(
                    session.getBlockChain() != null ? session.getBlockChain() : new ArrayList<>());
            intentService.triggerPostExecutionPipeline(
                    session.getId(), session.getUserId(), session.getId(), blockChain, selected);
        } catch (Exception e) {
            log.warn("Failed to trigger post-execution pipeline for sessionId={}: {}", session.getId(), e.getMessage());
        }
    }

    /** 积木链执行循环的可变迭代状态，避免方法间多值传递 */
    private static final class LoopState {
        List<Map<String, Object>> inputData;
        List<String> availableFields;
        String currentOutputType;
        int totalApiCalls;
        long totalTokens;

        LoopState(List<Map<String, Object>> inputData, List<String> availableFields,
                  String currentOutputType, int totalApiCalls, long totalTokens) {
            this.inputData = inputData;
            this.availableFields = availableFields;
            this.currentOutputType = currentOutputType;
            this.totalApiCalls = totalApiCalls;
            this.totalTokens = totalTokens;
        }
    }
}
