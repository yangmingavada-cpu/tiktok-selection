package com.tiktok.selection.mcp.hook.impl;

import com.tiktok.selection.mcp.McpObservation;
import com.tiktok.selection.mcp.hook.McpToolContext;
import com.tiktok.selection.mcp.hook.PostToolHook;
import org.springframework.stereotype.Component;

/**
 * 后置 Hook：规范化 Observation 结构。
 *
 * <p>确保 {@code success} 和 {@code message} 字段始终非 null，
 * 防止下游 Python Agent JSON 解析异常。
 *
 * <p>order=10，优先于 ExceptionCapturePostHook（order=20）执行。
 */
@Component
public class ResultStructurePostHook implements PostToolHook {

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public void postHandle(McpToolContext ctx, McpObservation result) {
        normalize(result);
    }

    @Override
    public void onError(McpToolContext ctx, Exception e, McpObservation result) {
        normalize(result);
    }

    private void normalize(McpObservation result) {
        if (result.getSuccess() == null) {
            result.setSuccess(false);
        }
        if (result.getMessage() == null && Boolean.TRUE.equals(result.getSuccess())) {
            result.setMessage("操作成功");
        }
    }
}
