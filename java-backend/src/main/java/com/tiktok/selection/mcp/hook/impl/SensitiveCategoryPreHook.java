package com.tiktok.selection.mcp.hook.impl;

import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.entity.User;
import com.tiktok.selection.entity.UserTier;
import com.tiktok.selection.mapper.UserTierMapper;
import com.tiktok.selection.mcp.hook.McpToolContext;
import com.tiktok.selection.mcp.hook.PreToolHook;
import com.tiktok.selection.service.McpToolConfigService;
import com.tiktok.selection.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 前置 Hook：敏感工具低 Tier 拦截。
 *
 * <p>若 {@code mcp_tool_config.sensitive_flag=true}，仅允许付费 Tier 用户（sortOrder > 1）调用。
 * 未认证调用（userId=null，如 Python Agent 内部调用）直接放行。
 *
 * <p>order=20，在 QuotaPreHook（order=10）之后执行。
 */
@Component
@RequiredArgsConstructor
public class SensitiveCategoryPreHook implements PreToolHook {

    private final McpToolConfigService toolConfigService;
    private final UserService userService;
    private final UserTierMapper userTierMapper;

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public void preHandle(McpToolContext ctx) throws BusinessException {
        // 未认证的内部调用直接放行
        if (ctx.getUserId() == null) {
            return;
        }

        // 工具未标记为敏感，直接放行
        if (!toolConfigService.isSensitive(ctx.getToolName())) {
            return;
        }

        // 查询用户等级
        User user = userService.getById(ctx.getUserId());
        if (user == null || user.getTierId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED,
                    "该工具（" + ctx.getToolName() + "）仅对付费用户开放");
        }

        UserTier tier = userTierMapper.selectById(user.getTierId());
        // sortOrder == 1 视为免费 Tier，不允许使用敏感工具
        if (tier == null || (tier.getSortOrder() != null && tier.getSortOrder() <= 1)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED,
                    "该工具（" + ctx.getToolName() + "）仅对付费用户开放，请升级套餐后使用");
        }
    }
}
