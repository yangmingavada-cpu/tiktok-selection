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
import com.tiktok.selection.entity.EchotikCategory;
import com.tiktok.selection.service.CategoryService;
import com.tiktok.selection.service.EchotikApiKeyService;
import com.tiktok.selection.service.IntentService;
import com.tiktok.selection.service.LlmConfigService;
import com.tiktok.selection.service.MemoryFileService;
import com.tiktok.selection.service.QuotaService;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.security.MessageDigest;

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
    private final CategoryService categoryService;
    private final MemoryFileService memoryFileService;

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
                             LlmConfigService llmConfigService,
                             CategoryService categoryService,
                             MemoryFileService memoryFileService) {
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
        this.categoryService       = categoryService;
        this.memoryFileService     = memoryFileService;
    }

    // ─── 字段中文映射 ──────────────────────────────────────────────────────────
    private static final Map<String, String> FIELD_LABELS;
    static {
        Map<String, String> m = new HashMap<>(160);
        // 商品基础
        m.put("product_id", "商品ID"); m.put("product_name", "商品名称"); m.put("region", "地区");
        m.put("category_id", "一级类目"); m.put("category_l2_id", "二级类目"); m.put("category_l3_id", "三级类目");
        m.put("min_price", "最低价"); m.put("max_price", "最高价"); m.put("spu_avg_price", "平均售价");
        m.put("discount", "折扣"); m.put("free_shipping", "包邮"); m.put("is_s_shop", "S级店铺");
        m.put("off_mark", "下架标记"); m.put("product_rating", "商品评分"); m.put("review_count", "评论数");
        m.put("product_commission_rate", "佣金率"); m.put("sales_flag", "销售标记");
        m.put("sales_trend_flag", "销售趋势标记"); m.put("cover_url", "封面图");
        m.put("first_crawl_dt", "首次抓取日期"); m.put("last_crawl_dt", "最后抓取日期");
        m.put("brand_name", "品牌名");
        // 销售数据
        m.put("total_sale_cnt", "总销量"); m.put("total_sale_gmv_amt", "总销售额");
        m.put("total_sale_1d_cnt", "1日销量"); m.put("total_sale_7d_cnt", "7日销量");
        m.put("total_sale_15d_cnt", "15日销量"); m.put("total_sale_30d_cnt", "30日销量");
        m.put("total_sale_60d_cnt", "60日销量"); m.put("total_sale_90d_cnt", "90日销量");
        m.put("total_sale_gmv_1d_amt", "1日销售额"); m.put("total_sale_gmv_7d_amt", "7日销售额");
        m.put("total_sale_gmv_15d_amt", "15日销售额"); m.put("total_sale_gmv_30d_amt", "30日销售额");
        m.put("total_sale_gmv_60d_amt", "60日销售额"); m.put("total_sale_gmv_90d_amt", "90日销售额");
        // 视频数据
        m.put("total_video_cnt", "总视频数"); m.put("total_video_7d_cnt", "7日视频数");
        m.put("total_video_30d_cnt", "30日视频数"); m.put("total_video_sale_cnt", "视频带货量");
        m.put("total_video_sale_7d_cnt", "7日视频带货量"); m.put("total_video_sale_30d_cnt", "30日视频带货量");
        m.put("total_video_sale_gmv_amt", "视频带货额"); m.put("total_video_sale_gmv_7d_amt", "7日视频带货额");
        m.put("total_video_sale_gmv_30d_amt", "30日视频带货额");
        // 直播数据
        m.put("total_live_cnt", "总直播数"); m.put("total_live_7d_cnt", "7日直播数");
        m.put("total_live_30d_cnt", "30日直播数"); m.put("total_live_sale_cnt", "直播带货量");
        m.put("total_live_sale_7d_cnt", "7日直播带货量"); m.put("total_live_sale_30d_cnt", "30日直播带货量");
        m.put("total_live_sale_gmv_amt", "直播带货额"); m.put("total_live_sale_gmv_7d_amt", "7日直播带货额");
        m.put("total_live_sale_gmv_30d_amt", "30日直播带货额");
        // 浏览/互动
        m.put("total_views_cnt", "总浏览量"); m.put("total_views_7d_cnt", "7日浏览量");
        m.put("total_views_30d_cnt", "30日浏览量"); m.put("total_views_1d_cnt", "1日浏览量");
        m.put("total_ifl_cnt", "总种草数"); m.put("total_digg_cnt", "总点赞数");
        m.put("total_digg_7d_cnt", "7日点赞数"); m.put("total_digg_30d_cnt", "30日点赞数");
        m.put("total_shares_cnt", "总分享数"); m.put("total_comments_cnt", "总评论数");
        m.put("total_favorites_cnt", "总收藏数");
        // 达人
        m.put("user_id", "用户ID"); m.put("unique_id", "账号"); m.put("nick_name", "昵称");
        m.put("category", "类目"); m.put("language", "语言"); m.put("gender", "性别");
        m.put("ec_score", "电商评分"); m.put("interaction_rate", "互动率"); m.put("show_case_flag", "橱窗标记");
        m.put("total_followers_cnt", "总粉丝数"); m.put("total_followers_1d_cnt", "1日涨粉");
        m.put("total_followers_7d_cnt", "7日涨粉"); m.put("total_followers_30d_cnt", "30日涨粉");
        m.put("total_followers_90d_cnt", "90日涨粉"); m.put("total_following_cnt", "关注数");
        m.put("total_post_video_cnt", "发布视频数"); m.put("total_product_cnt", "带货商品数");
        m.put("total_product_30d_cnt", "30日带货商品数"); m.put("avg_30d_price", "30日均价");
        m.put("per_video_product_views_avg_7d_cnt", "7日单视频均播"); m.put("bio", "简介");
        // 视频详情
        m.put("video_id", "视频ID"); m.put("video_desc", "视频描述");
        m.put("create_time", "创建时间"); m.put("duration", "时长");
        m.put("is_ad", "广告"); m.put("created_by_ai", "AI生成");
        // 店铺
        m.put("seller_id", "店铺ID"); m.put("seller_name", "店铺名称"); m.put("rating", "店铺评分");
        m.put("from_flag", "来源标记"); m.put("total_crawl_product_cnt", "抓取商品数");
        // 话题/关键词
        m.put("hashtag_id", "话题ID"); m.put("hashtag_name", "话题名称");
        m.put("video_count", "相关视频数"); m.put("total_views", "总播放量");
        m.put("avg_views_per_video", "平均播放量"); m.put("keyword", "关键词");
        m.put("search_volume", "搜索量"); m.put("search_trend", "搜索趋势");
        m.put("competition_level", "竞争程度");
        // 评论/情感
        m.put("review_id", "评论ID"); m.put("display_text", "评论内容");
        m.put("review_timestamp", "评论时间"); m.put("sku_id", "SKU ID");
        m.put("sku_specification", "SKU规格"); m.put("sentiment_positive", "正面情感");
        m.put("sentiment_negative", "负面情感"); m.put("sentiment_neutral", "中性情感");
        m.put("sentiment_summary", "情感摘要");
        // 趋势/富化
        m.put("desc_detail", "详情描述"); m.put("specification", "规格"); m.put("skus", "SKU列表");
        m.put("trend_data", "趋势数据"); m.put("trend_peak_date", "峰值日期");
        m.put("trend_direction", "趋势方向"); m.put("fans_growth_trend", "粉丝增长趋势");
        m.put("trend_views", "浏览趋势"); m.put("trend_likes", "点赞趋势");
        m.put("trend_comments", "评论趋势");
        // 计算/标注
        m.put("total_score", "总评分"); m.put("growth_rate_7d_30d", "7/30日增长率");
        m.put("profit_margin_est", "预估利润率"); m.put("contact_info", "联系方式");
        m.put("audience_demographics", "受众画像"); m.put("top_categories", "主要类目");
        m.put("llm_comment", "AI评语");
        FIELD_LABELS = Map.copyOf(m);
    }

    private static final Map<String, String> REGION_ZH = Map.of(
            "TH", "泰国", "US", "美国", "UK", "英国",
            "ID", "印度尼西亚", "MY", "马来西亚", "PH", "菲律宾",
            "VN", "越南", "SG", "新加坡", "SA", "沙特阿拉伯", "AE", "阿联酋"
    );

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

        // 执行前审计：调用 Python Audit Agent 检查积木链质量
        Map<String, Object> auditResult = intentService.auditBlockChain(blockChain);
        freshSession.setAuditResult(auditResult);
        sessionMapper.updateById(freshSession);
        if (!Boolean.TRUE.equals(auditResult.get("pass"))) {
            log.warn("Audit failed for sessionId={}: score={}, issues={}",
                    sessionId, auditResult.get("score"), auditResult.get("issues"));
            String auditMsg = "选品方案质量审核未通过（评分: " + auditResult.get("score") + "/100），建议调整后重试";
            failSession(freshSession, 0, 0L);
            sseEmitterManager.sendEvent(sessionId,
                    SseProgressEvent.fail(sessionId, 0, null, auditMsg));
            sseEmitterManager.complete(sessionId);
            return;
        }
        log.info("Audit passed for sessionId={}: score={}", sessionId, auditResult.get("score"));

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
        // 计算 blockChain 哈希前6位，用于记忆轮次标识
        String chainHash = computeChainHash(blockChain);

        // 扫描 blockChain 抽取 SCORE 块的 dimension_name 作为动态 label，供 buildDims 使用
        // executeAsync 和 resumeAsync 两条路径都经过这里，统一在这里填充一次即可
        state.dynamicLabels = extractDynamicLabels(blockChain);

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
                List<Map<String, Object>> stepDims = buildDims(state.availableFields, state.dynamicLabels);
                List<Map<String, Object>> displayData = translateForDisplay(state.inputData);
                updateCurrentView(sessionId, displayData, stepDims, state.currentOutputType);
                sendStepComplete(sessionId, seq, totalSteps, blockId, result, stepDims, displayData);

                // 每步执行后将原始数据写入记忆系统（用 agentThreadId 作为记忆隔离键）
                String label = blockDef.get("label") instanceof String l ? l : blockId;
                String memorySessionId = session.getAgentThreadId() != null ? session.getAgentThreadId() : sessionId;
                writeStepDataToMemory(session.getUserId(), memorySessionId, seq, blockId, label, config, result, displayData, stepDims, chainHash);
            }

            // 正常完成
            List<Map<String, Object>> finalDims = buildDims(state.availableFields, state.dynamicLabels);
            List<Map<String, Object>> finalDisplayData = translateForDisplay(state.inputData);
            completeSession(session, state.totalApiCalls, state.totalTokens, finalDisplayData, finalDims);
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
        List<Map<String, Object>> dims = buildDims(pauseFields, state.dynamicLabels);
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
                                   List<Map<String, Object>> dims,
                                   List<Map<String, Object>> displayData) {
        String msg = String.format("完成: %s，输出 %d 条",
                blockId, result.getOutputCount() != null ? result.getOutputCount() : 0);
        sseEmitterManager.sendEvent(sessionId,
                SseProgressEvent.stepComplete(sessionId, seq, total, blockId, msg,
                        displayData, dims));
    }

    /**
     * 将每步执行的原始数据写入记忆系统，供后续对话中 AI 检索回答用户提问。
     * 写入失败不影响主流程。
     */
    private void writeStepDataToMemory(String userId, String sessionId,
                                        int seq, String blockId, String label,
                                        Map<String, Object> config,
                                        BlockResult result,
                                        List<Map<String, Object>> displayData,
                                        List<Map<String, Object>> stepDims,
                                        String chainHash) {
        try {
            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            int outputCount = result.getOutputCount() != null ? result.getOutputCount() : 0;
            List<String> apis = result.getApiEndpointsCalled();
            String apiStr = (apis != null && !apis.isEmpty()) ? String.join(", ", apis) : "本地计算";

            // 字段列表（中文标签）
            String fieldsStr = stepDims.stream()
                    .map(d -> d.getOrDefault("label", d.get("id")).toString())
                    .collect(Collectors.joining(", "));

            // description 用于索引检索匹配
            String description = String.format("%s | blockId=%s | seq=%d | %s | %d条 | %s",
                    now, blockId, seq, apiStr, outputCount, fieldsStr.length() > 80 ? fieldsStr.substring(0, 80) : fieldsStr);

            // 构建 Markdown 正文
            StringBuilder content = new StringBuilder();
            content.append("## 执行信息\n");
            content.append("- 时间: ").append(now).append("\n");
            content.append("- 积木ID (blockId): ").append(blockId).append("\n");
            content.append("- 步骤序号 (seq): ").append(seq).append("\n");
            content.append("- 积木标签: ").append(label).append("\n");
            content.append("- 积木配置: ").append(config).append("\n");
            content.append("- API端点: ").append(apiStr).append("\n");
            content.append("- 耗时: ").append(result.getDurationMs()).append("ms\n");
            content.append("- API调用: ").append(result.getEchotikApiCalls()).append("次\n");
            content.append("- 输出数据量: ").append(outputCount).append("条\n\n");

            // 数据字段
            content.append("## 数据字段\n");
            for (Map<String, Object> dim : stepDims) {
                content.append("- ").append(dim.get("id")).append(" (").append(dim.getOrDefault("label", "")).append(")\n");
            }
            content.append("\n");

            // 数据明细（前50条，Markdown 表格）
            int dataLimit = Math.min(50, displayData.size());
            if (dataLimit > 0 && !stepDims.isEmpty()) {
                content.append("## 数据明细（前").append(dataLimit).append("条）\n");

                // 表头
                List<String> headers = stepDims.stream()
                        .map(d -> d.getOrDefault("label", d.get("id")).toString())
                        .toList();
                List<String> fieldIds = stepDims.stream()
                        .map(d -> d.get("id").toString())
                        .toList();
                content.append("| # | ").append(String.join(" | ", headers)).append(" |\n");
                content.append("|---|").append(headers.stream().map(h -> "---").collect(Collectors.joining("|"))).append("|\n");

                // 数据行
                for (int r = 0; r < dataLimit; r++) {
                    Map<String, Object> row = displayData.get(r);
                    content.append("| ").append(r + 1);
                    for (String fid : fieldIds) {
                        Object val = row.get(fid);
                        String cell = val != null ? val.toString().replace("|", "\\|").replace("\n", " ") : "";
                        if (cell.length() > 50) cell = cell.substring(0, 47) + "...";
                        content.append(" | ").append(cell);
                    }
                    content.append(" |\n");
                }
            }

            String hashSuffix = chainHash != null ? "-" + chainHash : "";
            String name = "执行-步骤" + seq + "-" + label + hashSuffix;
            memoryFileService.writeMemory(userId, sessionId, "session", "project",
                    name, description, content.toString(), "main", "执行", chainHash);

        } catch (Exception e) {
            log.warn("写入步骤记忆失败 sessionId={} seq={}: {}", sessionId, seq, e.getMessage());
        }
    }

    /** 检查 session 是否已被取消（直接读 DB） */
    /**
     * 计算 blockChain 内容的 MD5 哈希前6位，用于记忆轮次标识。
     */
    private static String computeChainHash(List<Map<String, Object>> blockChain) {
        try {
            String json = blockChain.toString();
            byte[] digest = MessageDigest.getInstance("MD5").digest(json.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.substring(0, 6);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean isCancelled(String sessionId) {
        Session s = sessionMapper.selectById(sessionId);
        return s == null || SessionStatusEnum.CANCELLED.getValue().equals(s.getStatus());
    }

    /**
     * 从积木链里抽取用户自定义字段的中文 label。
     * 当前覆盖：SCORE_NUMERIC / SCORE_SEMANTIC 块配置里的 output_field → dimension_name。
     * 这些字段是运行时动态生成的，不在静态 FIELD_LABELS 里，必须单独拎出来。
     */
    private Map<String, String> extractDynamicLabels(List<?> blockChain) {
        Map<String, String> result = new HashMap<>();
        if (blockChain == null) return result;
        for (Object block : blockChain) {
            if (!(block instanceof Map<?, ?> blockMap)) continue;
            Object blockIdObj = blockMap.get("blockId");
            if (!(blockIdObj instanceof String blockId)) continue;
            if (!"SCORE_NUMERIC".equals(blockId) && !"SCORE_SEMANTIC".equals(blockId)) continue;
            Object configObj = blockMap.get("config");
            if (!(configObj instanceof Map<?, ?> config)) continue;
            String outputField = config.get("output_field") instanceof String s ? s : null;
            String dimensionName = config.get("dimension_name") instanceof String s ? s : null;
            if (outputField != null && dimensionName != null && !dimensionName.isBlank()) {
                result.put(outputField, dimensionName);
            }
        }
        return result;
    }

    /**
     * 根据字段名列表构建前端 dims 定义，推断基础类型。
     * Label 查找优先级：dynamicLabels（用户自定义）> FIELD_LABELS（静态映射）> 字段 id 本身。
     */
    private List<Map<String, Object>> buildDims(List<String> fields, Map<String, String> dynamicLabels) {
        if (fields == null) return new ArrayList<>();
        return fields.stream().map(f -> {
            Map<String, Object> dim = LinkedHashMap.newLinkedHashMap(4);
            dim.put("id",    f);
            String label = dynamicLabels != null ? dynamicLabels.get(f) : null;
            if (label == null) {
                label = FIELD_LABELS.getOrDefault(f, f);
            }
            dim.put("label", label);
            dim.put("type",  inferFieldType(f));
            return dim;
        }).toList();
    }

    /**
     * 创建数据行的浅拷贝，并将 region 代码和 category ID 转换为中文显示名。
     * 返回新列表，不修改原始数据（避免影响后续 Block 逻辑）。
     */
    private List<Map<String, Object>> translateForDisplay(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return rows;

        // 从首行获取 region，用于查询该地区的类目映射
        Object firstRegion = rows.get(0).get("region");
        String regionCode = firstRegion instanceof String ? (String) firstRegion : null;

        Map<String, String> categoryNames = new HashMap<>();
        if (regionCode != null) {
            try {
                List<EchotikCategory> categories = categoryService.listByRegion(regionCode);
                for (EchotikCategory c : categories) {
                    categoryNames.put(c.getCategoryId(),
                            c.getNameZh() != null ? c.getNameZh() : c.getNameEn());
                }
            } catch (Exception e) {
                log.warn("Failed to load category names for region {}: {}", regionCode, e.getMessage());
            }
        }

        List<String> catKeys = List.of("category_id", "category_l2_id", "category_l3_id");
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new HashMap<>(row);
            // region 转中文
            Object rv = copy.get("region");
            if (rv instanceof String code) {
                copy.put("region", REGION_ZH.getOrDefault(code, code));
            }
            // category ID 转名称
            for (String key : catKeys) {
                Object cv = copy.get(key);
                if (cv != null) {
                    String catId = String.valueOf(cv);
                    copy.put(key, categoryNames.getOrDefault(catId, catId));
                }
            }
            result.add(copy);
        }
        return result;
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
     * agentThreadId 从 Session 实体读取（创建时由前端传入）。
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
                    session.getId(), session.getUserId(), session.getAgentThreadId(), blockChain, selected);
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
        /** 用户自定义字段的中文 label（如 SCORE 块的 output_field → dimension_name）；执行入口一次性填充 */
        Map<String, String> dynamicLabels;

        LoopState(List<Map<String, Object>> inputData, List<String> availableFields,
                  String currentOutputType, int totalApiCalls, long totalTokens) {
            this.inputData = inputData;
            this.availableFields = availableFields;
            this.currentOutputType = currentOutputType;
            this.totalApiCalls = totalApiCalls;
            this.totalTokens = totalTokens;
            this.dynamicLabels = new HashMap<>();
        }
    }
}
