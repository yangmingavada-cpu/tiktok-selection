package com.tiktok.selection.mcp.hook.impl;

import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.mcp.McpObservation;
import com.tiktok.selection.mcp.hook.McpToolContext;
import com.tiktok.selection.mcp.hook.PostToolHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 后置 Hook：异常捕获、格式化与 MDC 日志上报。
 *
 * <p>将工具执行抛出的异常统一转换为标准 error Observation，
 * 并通过 MDC 附加 toolName/userId/sessionId 上下文，
 * 方便 ELK / Loki 关联查询。
 *
 * <p>order=20，在 ResultStructurePostHook（order=10）之后执行。
 */
@Component
public class ExceptionCapturePostHook implements PostToolHook {

    private static final Logger log = LoggerFactory.getLogger(ExceptionCapturePostHook.class);

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public void postHandle(McpToolContext ctx, McpObservation result) {
        // 正常完成，无需处理
    }

    @Override
    public void onError(McpToolContext ctx, Exception e, McpObservation result) {
        MDC.put("toolName",  ctx.getToolName());
        MDC.put("userId",    ctx.getUserId() != null ? ctx.getUserId() : "anonymous");
        MDC.put("sessionId", ctx.getSessionId());
        try {
            String errorMsg;
            if (e instanceof BusinessException be) {
                // 业务异常：向用户暴露 message，code 写入 error 字段供前端分支处理
                errorMsg = "[" + be.getCode() + "] " + be.getMessage();
                log.warn("MCP_TOOL_BUSINESS_ERROR tool={} userId={} code={} msg={}",
                        ctx.getToolName(), ctx.getUserId(), be.getCode(), be.getMessage());
            } else {
                // 系统异常：隐藏堆栈，记录完整异常
                errorMsg = "工具执行异常，请稍后重试";
                log.error("MCP_TOOL_SYSTEM_ERROR tool={} userId={} sessionId={}",
                        ctx.getToolName(), ctx.getUserId(), ctx.getSessionId(), e);
            }
            result.setSuccess(false);
            result.setError(errorMsg);
        } finally {
            MDC.remove("toolName");
            MDC.remove("userId");
            MDC.remove("sessionId");
        }
    }
}
