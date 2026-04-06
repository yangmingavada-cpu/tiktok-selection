package com.tiktok.selection.mcp.hook.impl;

import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.mcp.hook.McpToolContext;
import com.tiktok.selection.mcp.hook.PreToolHook;
import com.tiktok.selection.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 前置 Hook：LLM 速率限制 + 月度 API 配额检查。
 *
 * <p>整合原先散落在 McpDispatcher 里的 {@code quotaService.checkLlmRateLimit()} 调用，
 * 同时接入月度配额执行（Redis INCR 实现）。
 *
 * <p>order=10，在 SensitiveCategoryPreHook（order=20）之前执行，
 * 限流拦截优先于权限拦截，避免无效的 DB 查询。
 */
@Component
@RequiredArgsConstructor
public class QuotaPreHook implements PreToolHook {

    private final QuotaService quotaService;

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public void preHandle(McpToolContext ctx) throws BusinessException {
        String userId = ctx.getUserId();

        // 1. 滑动窗口限流（每用户每小时 LLM Block 次数上限）
        quotaService.checkLlmRateLimit(userId);

        // 2. 月度 API 配额（Redis INCR，首次超限时抛出 BusinessException）
        quotaService.checkQuota(userId, 1);
    }
}
