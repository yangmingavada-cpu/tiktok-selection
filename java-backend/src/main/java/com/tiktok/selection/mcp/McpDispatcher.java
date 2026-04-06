package com.tiktok.selection.mcp;

import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.mcp.hook.McpToolContext;
import com.tiktok.selection.mcp.hook.PostToolHook;
import com.tiktok.selection.mcp.hook.PreToolHook;
import com.tiktok.selection.mcp.tool.McpTool;
import com.tiktok.selection.service.McpToolConfigService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MCP工具调用分发器。
 *
 * <p>遵循开闭原则（设计规约第13条）：通过Map自动注册所有{@link McpTool}实现，
 * 新增工具无需修改本类。
 *
 * <p>Hook 链：所有 {@link PreToolHook} / {@link PostToolHook} Bean 在启动时按 order 排序，
 * 每次工具调用自动执行，新增治理逻辑只需添加 Hook Bean。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class McpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(McpDispatcher.class);

    /** 每个 sessionId 一把锁，防止并发工具调用交叉写坏会话状态 */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    /** processedToolCallIds 单会话最大记录数，防止 Redis 中 JSON 无限膨胀 */
    private static final int MAX_TOOL_CALL_IDS = 200;

    private final Map<String, McpTool> toolMap;
    private final ChainBuildSessionManager sessionManager;
    private final McpToolConfigService toolConfigService;

    private final List<PreToolHook>  rawPreHooks;
    private final List<PostToolHook> rawPostHooks;

    /** 按 order 升序排列后的 Pre Hook 链 */
    private List<PreToolHook>  sortedPreHooks;
    /** 按 order 升序排列后的 Post Hook 链 */
    private List<PostToolHook> sortedPostHooks;

    @Autowired
    public McpDispatcher(List<McpTool> tools,
                         ChainBuildSessionManager sessionManager,
                         McpToolConfigService toolConfigService,
                         List<PreToolHook> preHooks,
                         List<PostToolHook> postHooks) {
        this.toolMap = HashMap.newHashMap(tools.size());
        for (McpTool tool : tools) {
            toolMap.put(tool.getToolName(), tool);
            log.info("Registered MCP tool: {}", tool.getToolName());
        }
        this.sessionManager    = sessionManager;
        this.toolConfigService = toolConfigService;
        this.rawPreHooks       = preHooks;
        this.rawPostHooks      = postHooks;
    }

    @PostConstruct
    void sortHooks() {
        sortedPreHooks  = rawPreHooks.stream()
                .sorted(Comparator.comparingInt(PreToolHook::getOrder))
                .toList();
        sortedPostHooks = rawPostHooks.stream()
                .sorted(Comparator.comparingInt(PostToolHook::getOrder))
                .toList();
        log.info("MCP Hook chain initialized: preHooks={} postHooks={}",
                sortedPreHooks.size(), sortedPostHooks.size());
    }

    /**
     * 分发工具调用。
     *
     * @param name       工具名称
     * @param arguments  工具参数
     * @param sessionId  构建会话ID
     * @param toolCallId 幂等性ID（可选）
     * @param userId     发起请求的用户ID，用于限流校验
     * @return Observation
     */
    public McpObservation dispatch(String name, Map<String, Object> arguments,
                                   String sessionId, String toolCallId, String userId) {
        ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        lock.lock();
        try {
            return doDispatch(name, arguments, sessionId, toolCallId, userId);
        } finally {
            lock.unlock();
        }
    }

    private McpObservation doDispatch(String name, Map<String, Object> arguments,
                                      String sessionId, String toolCallId, String userId) {
        // 获取或创建构建会话
        ChainBuildSession session = sessionManager.getOrCreate(sessionId);

        // 记录userId（首次写入后保持稳定，avoid覆盖为null）
        if (userId != null && session.getUserId() == null) {
            session.setUserId(userId);
        }

        // 幂等性检查：重复调用直接返回，不计入限流
        if (toolCallId != null && session.getProcessedToolCallIds().contains(toolCallId)) {
            return McpObservation.builder()
                    .success(true)
                    .message("重复调用已忽略（幂等）")
                    .chainLength(session.getBlocks().size())
                    .dataType(session.getCurrentOutputType())
                    .availableFields(session.getAvailableFields())
                    .estimatedRows(session.getEstimatedRowCount())
                    .scoreFields(session.getScoreFields())
                    .build();
        }

        // 工具存在性检查（早于 Hook 链，避免无意义的限流计数）
        McpTool tool = toolMap.get(name);
        if (tool == null) {
            return McpObservation.builder().success(false).error("未知工具: " + name).build();
        }
        if (!toolConfigService.isToolEnabled(name)) {
            return McpObservation.builder().success(false).error("工具已被管理员禁用: " + name).build();
        }

        log.info("MCP_TOOL_CALL userId={} sessionId={} tool={} step={} args={}",
                session.getUserId(), sessionId, name, session.getSeqCounter() + 1, arguments);

        // 构建 Hook 上下文
        McpToolContext ctx = McpToolContext.builder()
                .toolName(name)
                .toolTag(tool.getTag())
                .userId(session.getUserId())
                .sessionId(sessionId)
                .arguments(arguments)
                .session(session)
                .build();

        // ── Pre Hook 链 ──────────────────────────────────────────────────────
        for (PreToolHook hook : sortedPreHooks) {
            try {
                hook.preHandle(ctx);
            } catch (BusinessException be) {
                McpObservation errResult = McpObservation.builder()
                        .success(false)
                        .error("[" + be.getCode() + "] " + be.getMessage())
                        .build();
                runPostErrorHooks(ctx, be, errResult);
                return errResult;
            }
        }

        // ── 工具执行 ─────────────────────────────────────────────────────────
        McpObservation result;
        try {
            result = tool.execute(session, arguments);
        } catch (Exception e) {
            result = McpObservation.builder().success(false).build();
            runPostErrorHooks(ctx, e, result);
            return result;
        }

        // ── Post Hook 链 ─────────────────────────────────────────────────────
        for (PostToolHook hook : sortedPostHooks) {
            hook.postHandle(ctx, result);
        }

        if (Boolean.FALSE.equals(result.getSuccess())) {
            log.warn("MCP_TOOL_FAIL userId={} sessionId={} tool={} error={}",
                    session.getUserId(), sessionId, name, result.getError());
        }

        if (Boolean.TRUE.equals(result.getSuccess()) && "finalize_chain".equals(name)) {
            log.info("MCP_CHAIN_FINAL userId={} sessionId={} chainLength={} blocks={}",
                    session.getUserId(), sessionId, session.getBlocks().size(), session.getBlocks());
        }

        // 记录tool_call_id防重复（限制集合大小，防止Redis JSON无限膨胀）
        if (toolCallId != null && Boolean.TRUE.equals(result.getSuccess())
                && session.getProcessedToolCallIds().size() < MAX_TOOL_CALL_IDS) {
            session.getProcessedToolCallIds().add(toolCallId);
        }

        // 持久化会话状态
        sessionManager.save(session);

        // 注入积木链快照：Python Agent 存入 checkpoint，用于服务重启后恢复 ChainBuildSession
        if (Boolean.TRUE.equals(result.getSuccess()) && !session.getBlocks().isEmpty()) {
            result.setChainSnapshot(new ArrayList<>(session.getBlocks()));
        }

        return result;
    }

    private void runPostErrorHooks(McpToolContext ctx, Exception e, McpObservation result) {
        for (PostToolHook hook : sortedPostHooks) {
            hook.onError(ctx, e, result);
        }
    }
}
