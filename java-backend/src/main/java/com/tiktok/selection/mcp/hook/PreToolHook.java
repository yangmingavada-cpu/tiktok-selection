package com.tiktok.selection.mcp.hook;

import com.tiktok.selection.common.BusinessException;

/**
 * MCP 工具调用前置 Hook。
 *
 * <p>在工具执行前按 {@link #getOrder()} 升序执行。
 * 若抛出 {@link BusinessException}，整个调用链立即中止，Dispatcher 返回错误 Observation。
 */
public interface PreToolHook {

    /** 执行顺序（数字越小越先执行） */
    int getOrder();

    /**
     * 前置处理。
     *
     * @param ctx 工具调用上下文
     * @throws BusinessException 中止调用链并返回错误响应
     */
    void preHandle(McpToolContext ctx) throws BusinessException;
}
