package com.tiktok.selection.mcp.hook;

import com.tiktok.selection.mcp.McpObservation;

/**
 * MCP 工具调用后置 Hook。
 *
 * <p>在工具执行后按 {@link #getOrder()} 升序执行。
 * {@link #onError} 在工具抛出异常时调用，用于统一错误格式化与日志上报。
 */
public interface PostToolHook {

    /** 执行顺序（数字越小越先执行） */
    int getOrder();

    /**
     * 工具执行成功后调用。可修改 {@code result} 内容。
     *
     * @param ctx    工具调用上下文
     * @param result 工具返回的 Observation（可就地修改）
     */
    void postHandle(McpToolContext ctx, McpObservation result);

    /**
     * 工具执行抛出异常时调用。应将 {@code e} 序列化为标准错误格式写入 {@code result}。
     *
     * @param ctx    工具调用上下文
     * @param e      工具抛出的异常
     * @param result 待填充的 Observation（此时 success=false）
     */
    void onError(McpToolContext ctx, Exception e, McpObservation result);
}
