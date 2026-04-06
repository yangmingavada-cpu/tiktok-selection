package com.tiktok.selection.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * MCP JSON-RPC 2.0 端点
 * Python Agent通过POST /mcp/jsonrpc 调用
 * 支持: tools/list, tools/call, resources/read
 *
 * @author system
 * @date 2026/03/24
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);
    private static final String META_KEY = "_meta";
    private static final String SESSION_ID_KEY = "session_id";
    private static final String TOOL_CALL_ID_KEY = "tool_call_id";
    private static final String USER_ID_KEY = "user_id";
    private static final String SUCCESS_KEY = "success";

    private final McpToolRegistry toolRegistry;
    private final McpDispatcher dispatcher;
    private final McpResourceProvider resourceProvider;
    private final ChainBuildSessionManager sessionManager;
    private final IntentProgressBus progressBus;

    public McpController(McpToolRegistry toolRegistry,
                         McpDispatcher dispatcher,
                         McpResourceProvider resourceProvider,
                         ChainBuildSessionManager sessionManager,
                         IntentProgressBus progressBus) {
        this.toolRegistry = toolRegistry;
        this.dispatcher = dispatcher;
        this.resourceProvider = resourceProvider;
        this.sessionManager = sessionManager;
        this.progressBus = progressBus;
    }

    /**
     * JSON-RPC 2.0 统一入口
     */
    @PostMapping("/jsonrpc")
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(@RequestBody Map<String, Object> request) {
        Object id = request.get("id");
        String method = (String) request.get("method");
        Map<String, Object> params = request.get("params") instanceof Map<?, ?>
                ? (Map<String, Object>) request.get("params")
                : new HashMap<>();

        // 规约日志第6条：debug级别输出必须判断开关，避免参数求值开销
        if (log.isDebugEnabled()) {
            log.debug("MCP request: method={}, id={}", method, id);
        }

        try {
            Object result = route(method, params);
            return jsonRpcSuccess(id, result);
        } catch (Exception e) {
            log.error("MCP error: method={}, error={}", method, e.getMessage(), e);
            // 规约第4条（P0-3修复）：禁止将内部异常原文返回调用方，防止泄露系统结构信息。
            // IllegalArgumentException 属于可预期的参数错误，允许透传其消息。
            String safeMsg = (e instanceof IllegalArgumentException)
                    ? e.getMessage()
                    : "MCP内部错误，请检查请求参数";
            return jsonRpcError(id, -32000, safeMsg);
        }
    }

    @SuppressWarnings("unchecked")
    private Object route(String method, Map<String, Object> params) {
        String sessionId = extractSessionId(params);

        return switch (method) {
            case "tools/list" -> {
                ChainBuildSession session = sessionManager.getOrCreate(sessionId);
                yield Map.of("tools", toolRegistry.listTools(session));
            }
            case "tools/call" -> {
                String name = (String) params.get("name");
                Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?>
                        ? (Map<String, Object>) params.get("arguments")
                        : new HashMap<>();
                String toolCallId = extractToolCallId(params);
                String userId = extractUserId(params);

                if (name == null) {
                    throw new IllegalArgumentException("tools/call缺少name参数");
                }
                McpObservation obs = dispatcher.dispatch(name, arguments, sessionId, toolCallId, userId);

                // 推送步骤进度事件给前端
                String label = obs.getMessage() != null ? obs.getMessage() : name;
                progressBus.publish(sessionId, "step", Map.of(
                        "tool", name,
                        "label", label,
                        SUCCESS_KEY, Boolean.TRUE.equals(obs.getSuccess()),
                        "seq", obs.getChainLength() != null ? obs.getChainLength() : 0
                ));

                yield observationToMap(obs);
            }
            case "resources/read" -> {
                String uri = (String) params.get("uri");
                // 推送知识库查询事件（轻量提示，seq=0不计入积木数）
                progressBus.publish(sessionId, "step", Map.of(
                        "tool", "read_resource",
                        "label", "查询知识库: " + uri,
                        SUCCESS_KEY, true,
                        "seq", 0
                ));
                yield resourceProvider.read(uri, sessionId);
            }
            case "initialize" -> {
                // MCP协议初始化握手
                handleInitialize(params, sessionId);
                yield Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of("tools", Map.of(), "resources", Map.of()),
                        "serverInfo", Map.of("name", "tiktok-selection-mcp", "version", "1.0.0")
                );
            }
            case "notify/thinking" -> {
                // Python Agent 推送 LLM 实时 token，转发给前端 SSE
                String text = params.get("text") instanceof String s ? s : "";
                progressBus.publish(sessionId, "thinking", Map.of("text", text));
                yield Map.of(SUCCESS_KEY, true);
            }
            default -> throw new IllegalArgumentException("未知MCP方法: " + method);
        };
    }

    /**
     * 处理initialize请求（增量模式时从session_context恢复状态）
     */
    @SuppressWarnings("unchecked")
    private void handleInitialize(Map<String, Object> params, String sessionId) {
        Map<String, Object> meta = params.get(META_KEY) instanceof Map<?, ?>
                ? (Map<String, Object>) params.get(META_KEY)
                : null;

        if (meta != null) {
            Map<String, Object> sessionContext = meta.get("session_context") instanceof Map<?, ?>
                    ? (Map<String, Object>) meta.get("session_context")
                    : null;
            if (sessionContext != null && sessionId != null) {
                sessionManager.initFromContext(sessionId, sessionContext);
                log.info("MCP initialized in incremental mode: sessionId={}", sessionId);
                return;
            }
        }

        // 新建模式：确保ChainBuildSession存在
        sessionManager.getOrCreate(sessionId);
        log.info("MCP initialized in new mode: sessionId={}", sessionId);
    }

    @SuppressWarnings("unchecked")
    private String extractSessionId(Map<String, Object> params) {
        Object meta = params.get(META_KEY);
        if (meta instanceof Map<?, ?> metaMap) {
            Object sid = ((Map<String, Object>) metaMap).get(SESSION_ID_KEY);
            if (sid instanceof String s) return s;
        }
        // 如果没有session_id，使用默认值（不推荐）
        return "default";
    }

    @SuppressWarnings("unchecked")
    private String extractToolCallId(Map<String, Object> params) {
        Object meta = params.get(META_KEY);
        if (meta instanceof Map<?, ?> metaMap) {
            Object tcid = ((Map<String, Object>) metaMap).get(TOOL_CALL_ID_KEY);
            if (tcid instanceof String s) return s;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractUserId(Map<String, Object> params) {
        Object meta = params.get(META_KEY);
        if (meta instanceof Map<?, ?> metaMap) {
            Object uid = ((Map<String, Object>) metaMap).get(USER_ID_KEY);
            if (uid instanceof String s) return s;
        }
        return null;
    }

    private Map<String, Object> observationToMap(McpObservation obs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(SUCCESS_KEY, Boolean.TRUE.equals(obs.getSuccess()));
        if (obs.getType() != null) map.put("type", obs.getType());
        if (obs.getMessage() != null) map.put("message", obs.getMessage());
        if (obs.getSuggestions() != null) map.put("suggestions", obs.getSuggestions());
        map.put("chain_length", obs.getChainLength());
        if (obs.getDataType() != null) map.put("data_type", obs.getDataType());
        if (obs.getAvailableFields() != null) map.put("available_fields", obs.getAvailableFields());
        map.put("estimated_rows", obs.getEstimatedRows());
        if (obs.getScoreFields() != null) map.put("score_fields", obs.getScoreFields());
        if (obs.getCostEstimate() != null) map.put("cost_estimate", obs.getCostEstimate());
        if (obs.getHint() != null) map.put("hint", obs.getHint());
        if (obs.getError() != null) map.put("error", obs.getError());
        if (obs.getBlockChain() != null) map.put("block_chain", obs.getBlockChain());
        if (obs.getChainSnapshot() != null) map.put("chain_snapshot", obs.getChainSnapshot());
        if (obs.getSelectionPlan() != null) map.put("selection_plan", obs.getSelectionPlan());
        return map;
    }

    private Map<String, Object> jsonRpcSuccess(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> jsonRpcError(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message != null ? message : "Internal error"));
        return response;
    }
}
