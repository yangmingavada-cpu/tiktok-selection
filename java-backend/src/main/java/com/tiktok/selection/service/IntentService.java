package com.tiktok.selection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.entity.IntentParseLog;
import com.tiktok.selection.entity.LlmConfig;
import com.tiktok.selection.mapper.IntentParseLogMapper;
import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.ChainBuildSessionManager;
import com.tiktok.selection.mcp.IntentProgressBus;
import com.tiktok.selection.service.MemoryFileService.MemoryIndexEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 意图解析服务
 * 负责调用Python AI服务的/intent/parse接口，传递LLM配置和MCP端点
 *
 * @author system
 * @date 2026/03/24
 */
@Service
public class IntentService {

    private static final Logger log = LoggerFactory.getLogger(IntentService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(615);

    private final LlmConfigService llmConfigService;
    private final ChainBuildSessionManager sessionManager;
    private final IntentProgressBus progressBus;
    private final IntentParseLogMapper parseLogMapper;
    private final MemoryFileService memoryFileService;
    private final WebClient webClient;

    @Value("${python.ai.base-url:http://localhost:8000}")
    private String pythonAiBaseUrl;

    /** Agent 构建积木链的 token 预算，防止 LLM 无限循环 */
    @Value("${ai.budget.intent-parse-token-quota:200000}")
    private int intentTokenQuota;

    /** 方案解读阶段的 token 预算，避免报告生成过长 */
    @Value("${ai.budget.intent-interpret-token-quota:6000}")
    private int interpretTokenQuota;

    /** MCP JSON-RPC 自身服务地址，用于 Python Agent 回调，从配置读取避免 localhost 硬编码 */
    @Value("${mcp.internal.self-url:http://localhost:8080}")
    private String mcpSelfUrl;

    public IntentService(LlmConfigService llmConfigService,
                         ChainBuildSessionManager sessionManager,
                         IntentProgressBus progressBus,
                         IntentParseLogMapper parseLogMapper,
                         MemoryFileService memoryFileService,
                         WebClient.Builder webClientBuilder) {
        this.llmConfigService  = llmConfigService;
        this.sessionManager    = sessionManager;
        this.progressBus       = progressBus;
        this.parseLogMapper    = parseLogMapper;
        this.memoryFileService = memoryFileService;
        // 配置 Netty 底层超时：responseTimeout 覆盖默认值，与 Reactor .timeout() 保持一致
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(TIMEOUT);
        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 解析用户意图，通过Python ReAct Agent构建block_chain
     *
     * @param userText       用户自然语言输入
     * @param sessionContext 增量模式下传入的当前Session状态（新建时为null）
     * @param buildSessionId 前端提供的构建会话ID（用于SSE关联）
     * @param userId         发起请求的用户ID（用于配额统计）
     * @return 包含block_chain的解析结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseIntent(String userText, Map<String, Object> sessionContext,
                                           String buildSessionId, String agentThreadId,
                                           String conversationSummary,
                                           List<Map<String, String>> qaHistory,
                                           String userId) {
        // 使用前端提供的sessionId（用于SSE关联），否则自动生成
        if (buildSessionId == null || buildSessionId.isBlank()) {
            buildSessionId = UUID.randomUUID().toString().replace("-", "");
        }
        final String sessionId = buildSessionId;

        // 初始化构建会话
        if (sessionContext != null) {
            sessionManager.initFromContext(sessionId, sessionContext);
        } else {
            sessionManager.getOrCreate(sessionId);
        }

        // 获取LLM配置
        LlmConfig llmConfig = llmConfigService.getActiveLlmConfig();
        Map<String, Object> llmConfigMap = llmConfigService.toLlmConfigMap(llmConfig);

        // MCP端点：从配置读取服务自身地址，避免 localhost 硬编码在容器环境下失效
        String mcpEndpoint = mcpSelfUrl + "/mcp/jsonrpc";

        // 构建请求体（userText 不写入日志，避免 PII 泄露）
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("session_id", buildSessionId);
        requestBody.put("agent_thread_id", agentThreadId);
        requestBody.put("user_id", userId);
        requestBody.put("user_text", userText);
        requestBody.put("conversation_summary", conversationSummary);
        requestBody.put("session_context", sessionContext);
        requestBody.put("llm_config", llmConfigMap);
        requestBody.put("mcp_endpoint", mcpEndpoint);
        requestBody.put("token_quota", intentTokenQuota);
        if (qaHistory != null && !qaHistory.isEmpty()) {
            requestBody.put("qa_history", qaHistory);
        }

        log.info("Calling Python intent parse: buildSessionId={}", buildSessionId);

        // 立即推送第一个 step 事件，让前端脱离空白"..."状态，显示进度提示
        progressBus.publish(sessionId, "step", Map.of(
                "tool", "thinking",
                "label", "AI正在分析需求，预计1-3分钟…",
                "success", true,
                "seq", 0
        ));

        try {
            Map<String, Object> result = webClient.post()
                    .uri(pythonAiBaseUrl + "/intent/parse")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .block();

            if (result == null) {
                throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "Python服务返回空响应");
            }

            int tokensUsed = result.get("llm_tokens_used") instanceof Number n ? n.intValue() : 0;
            String resultType = result.get("type") instanceof String s ? s : null;
            if (tokensUsed >= intentTokenQuota && !"needs_input".equals(resultType)) {
                result.put("success", false);
                result.put("message", "AI 解析超出安全预算，请补充更明确的需求后重试");
                log.warn("Intent parse exceeded token quota: sessionId={}, tokens={}, quota={}",
                        sessionId, tokensUsed, intentTokenQuota);
            }

            // needs_input: AI主动暂停询问用户，读取当前partial链写入响应，再清理会话
            if ("needs_input".equals(result.get("type"))) {
                enrichNeedsInputResult(result, sessionId);
            }

            // 清理临时构建会话，推送完成事件
            sessionManager.delete(sessionId);
            progressBus.complete(sessionId);

            boolean success = Boolean.TRUE.equals(result.get("success"));
            int iters  = result.get("iterations")      instanceof Number n ? n.intValue() : 0;
            saveParseLog(userId, sessionId, tokensUsed, iters, success, userText, qaHistory, result);

            log.info("Intent parse completed: buildSessionId={}, success={}, tokens={}",
                    sessionId, success, tokensUsed);
            return result;

        } catch (WebClientResponseException e) {
            sessionManager.delete(sessionId);
            saveParseLog(userId, sessionId, 0, 0, false, userText, qaHistory, null);
            String responseBody;
            try {
                responseBody = e.getResponseBodyAsString();
            } catch (Exception bodyEx) {
                log.debug("Failed to read response body from WebClientResponseException", bodyEx);
                responseBody = "(unable to read response body)";
            }
            log.error("Python service error: status={}, body={}", e.getStatusCode(), responseBody, e);
            progressBus.error(sessionId, "AI服务调用失败: " + e.getStatusCode());
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR,
                    "AI服务调用失败: " + e.getStatusCode() + " - " + responseBody, e);
        } catch (BusinessException e) {
            sessionManager.delete(sessionId);
            saveParseLog(userId, sessionId, 0, 0, false, userText, qaHistory, null);
            progressBus.error(sessionId, e.getMessage());
            log.error("Intent parse business error: buildSessionId={}, msg={}", sessionId, e.getMessage());
            throw e;
        } catch (Exception e) {
            sessionManager.delete(sessionId);
            saveParseLog(userId, sessionId, 0, 0, false, userText, qaHistory, null);
            progressBus.error(sessionId, "意图解析失败");
            log.error("Intent parse failed: buildSessionId={}", sessionId, e);
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "意图解析失败: " + e.getMessage(), e);
        }
    }

    private void enrichNeedsInputResult(Map<String, Object> result, String sessionId) {
        ChainBuildSession partialSession = sessionManager.get(sessionId);
        if (partialSession != null && partialSession.getBlocks() != null) {
            result.put("partialBlockChain", partialSession.getBlocks());
        }
    }

    private void saveParseLog(String userId, String buildSessionId,
                              int tokens, int iterations, boolean success,
                              String userText, List<Map<String, String>> qaHistory,
                              Map<String, Object> result) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            IntentParseLog entry = new IntentParseLog();
            entry.setUserId(userId);
            entry.setBuildSessionId(buildSessionId);
            entry.setLlmTokensUsed(tokens);
            entry.setIterations(iterations);
            entry.setApiCalls(Math.max(1, iterations));
            entry.setSuccess(success);
            entry.setUserText(userText);
            if (qaHistory != null && !qaHistory.isEmpty()) {
                entry.setQaHistory(mapper.writeValueAsString(qaHistory));
            }
            if (result != null) {
                String type = (String) result.get("type");
                entry.setResponseType(type != null ? type : (success ? "block_chain" : "error"));
                String msg = (String) result.get("message");
                if (msg == null) msg = (String) result.get("summary");
                entry.setResponseMessage(msg);
                Object chain = result.get("block_chain");
                if (chain != null) {
                    entry.setBlockChain(mapper.writeValueAsString(chain));
                }
                Object suggs = result.get("suggestions");
                if (suggs != null) {
                    entry.setSuggestions(mapper.writeValueAsString(suggs));
                }
            } else {
                entry.setResponseType("error");
            }
            parseLogMapper.insert(entry);
        } catch (Exception ex) {
            log.warn("Failed to save intent parse log: {}", ex.getMessage());
        }
    }

    /**
     * 将积木链解读为用户友好的 Markdown 方案报告
     *
     * @param blockChain 待解读的积木链
     * @return 包含 interpretation 字段的结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> interpretBlockChain(List<Map<String, Object>> blockChain) {
        LlmConfig llmConfig = llmConfigService.getActiveLlmConfig();
        Map<String, Object> llmConfigMap = llmConfigService.toLlmConfigMap(llmConfig);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("block_chain", blockChain);
        requestBody.put("llm_config", llmConfigMap);
        requestBody.put("token_quota", interpretTokenQuota);

        try {
            Map<String, Object> result = webClient.post()
                    .uri(pythonAiBaseUrl + "/intent/interpret")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            int tokensUsed = result != null && result.get("llm_tokens_used") instanceof Number n ? n.intValue() : 0;
            if (result != null && tokensUsed >= interpretTokenQuota) {
                result.put("success", false);
                result.put("message", "AI 方案解读超出安全预算，请精简方案后重试");
                log.warn("Interpret exceeded token quota: tokens={}, quota={}", tokensUsed, interpretTokenQuota);
            }
            return result != null ? result : Map.of("success", false, "interpretation", "");
        } catch (Exception e) {
            log.error("Interpret block chain failed: {}", e.getMessage());
            return Map.of("success", false, "interpretation", "");
        }
    }

    /**
     * 流式解读积木链：订阅 Python SSE 流，逐 token 转发给前端 SseEmitter。
     *
     * @param blockChain 待解读的积木链
     * @param userId     用户ID，用于加载用户画像记忆以个性化报告风格（可为 null）
     * @param sessionId  会话ID（当前未使用，预留给未来蒸馏联动）
     */
    public SseEmitter interpretBlockChainStream(List<Map<String, Object>> blockChain,
                                                String userId, String sessionId) {
        SseEmitter emitter = new SseEmitter(120_000L);

        LlmConfig llmConfig = llmConfigService.getActiveLlmConfig();
        Map<String, Object> llmConfigMap = llmConfigService.toLlmConfigMap(llmConfig);

        // 加载用户画像记忆（memoryType=user），注入 Python 以个性化解读报告风格
        String userMemories = loadUserMemories(userId);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("block_chain", blockChain);
        requestBody.put("llm_config", llmConfigMap);
        requestBody.put("token_quota", interpretTokenQuota);
        if (userMemories != null) {
            requestBody.put("user_memories", userMemories);
        }

        webClient.post()
                .uri(pythonAiBaseUrl + "/intent/interpret-stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(
                        data -> {
                            try {
                                emitter.send(SseEmitter.event().data(data, MediaType.APPLICATION_JSON));
                            } catch (Exception ignored) {}
                        },
                        error -> {
                            log.error("Interpret stream failed: {}", error.getMessage());
                            try {
                                emitter.send(SseEmitter.event().name("error")
                                        .data("{\"error\":\"stream failed\"}"));
                            } catch (Exception ignored) {}
                            emitter.complete();
                        },
                        emitter::complete
                );

        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    /**
     * 加载指定用户的 user 类型记忆内容，拼接成字符串供 Python Agent 使用。
     * userId 为 null 或无记忆时返回 null。
     */
    private String loadUserMemories(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            List<MemoryIndexEntry> index = memoryFileService.listIndex(userId, null);
            List<String> userFilePaths = index.stream()
                    .filter(e -> "user".equals(e.memoryType()))
                    .map(MemoryIndexEntry::filePath)
                    .collect(Collectors.toList());
            if (userFilePaths.isEmpty()) {
                return null;
            }
            Map<String, String> contents = memoryFileService.readFiles(userId, userFilePaths);
            if (contents.isEmpty()) {
                return null;
            }
            return String.join("\n\n---\n\n", contents.values());
        } catch (IOException e) {
            log.warn("Failed to load user memories for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 获取构建会话的当前状态（用于前端轮询或调试）
     */
    public ChainBuildSession getChainBuildSession(String buildSessionId) {
        return sessionManager.get(buildSessionId);
    }

    /**
     * 执行完成后的三路并行后处理管道（fire-and-forget）：
     * 1. 蒸馏：从 checkpoint 提取跨会话记忆
     * 2. 竞品洞察：分析执行结果（SSE 推送，此处 fire-and-forget 形式调用）
     *
     * <p>agentThreadId 为 LangGraph checkpoint 线程 ID，缺省时使用 sessionId。
     * 两路请求均通过 subscribe() 非阻塞发起，不等待响应。
     *
     * @param sessionId     执行会话ID
     * @param userId        用户ID
     * @param agentThreadId 规划 Agent checkpoint 线程ID（null 时回落到 sessionId）
     * @param blockChain    积木链（传给蒸馏端点作为上下文补充）
     * @param selectedProducts 执行结果商品列表（传给竞品洞察端点）
     */
    public void triggerPostExecutionPipeline(String sessionId, String userId,
                                             String agentThreadId,
                                             List<Map<String, Object>> blockChain,
                                             List<Map<String, Object>> selectedProducts) {
        String effectiveThreadId = (agentThreadId != null) ? agentThreadId : sessionId;

        LlmConfig llmConfig = llmConfigService.getActiveLlmConfig();
        Map<String, Object> llmConfigMap = llmConfigService.toLlmConfigMap(llmConfig);

        // 1. 蒸馏（fire-and-forget, 202）
        Map<String, Object> distillBody = new LinkedHashMap<>();
        distillBody.put("user_id",        userId);
        distillBody.put("session_id",     sessionId);
        distillBody.put("agent_thread_id", effectiveThreadId);
        distillBody.put("block_chain",    blockChain);

        webClient.post()
                .uri(pythonAiBaseUrl + "/intent/distillation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(distillBody)
                .retrieve()
                .bodyToMono(Void.class)
                .subscribe(
                        null,
                        err -> log.warn("Distillation trigger failed sessionId={}: {}", sessionId, err.getMessage())
                );

        // 2. 竞品洞察（fire-and-forget, consume SSE stream to completion）
        if (selectedProducts != null && !selectedProducts.isEmpty()) {
            Map<String, Object> competitorBody = new LinkedHashMap<>();
            competitorBody.put("selected_products", selectedProducts);
            competitorBody.put("llm_config",        llmConfigMap);

            webClient.post()
                    .uri(pythonAiBaseUrl + "/intent/competitor-stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(competitorBody)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .subscribe(
                            null,
                            err -> log.warn("Competitor stream failed sessionId={}: {}", sessionId, err.getMessage())
                    );
        }

        log.info("POST_EXEC_PIPELINE sessionId={} userId={} threadId={}", sessionId, userId, effectiveThreadId);
    }
}
