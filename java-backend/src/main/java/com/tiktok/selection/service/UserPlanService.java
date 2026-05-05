package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.dto.request.PlanCreateRequest;
import com.tiktok.selection.entity.PresetPackage;
import com.tiktok.selection.entity.UserPlan;
import com.tiktok.selection.mapper.UserPlanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * 用户方案服务
 *
 * @author system
 * @date 2026/03/24
 */
@Service
public class UserPlanService extends ServiceImpl<UserPlanMapper, UserPlan> {

    private final PresetPackageService presetPackageService;

    @Autowired
    public UserPlanService(@Lazy PresetPackageService presetPackageService) {
        this.presetPackageService = presetPackageService;
    }

    public UserPlan createPlan(String userId, PlanCreateRequest request) {
        UserPlan plan = new UserPlan();
        plan.setUserId(userId);
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setSourceText(request.getSourceText());
        plan.setBlockChain(request.getBlockChain());
        plan.setTags(request.getTags());
        plan.setPublicFlag(false);
        plan.setUseCount(0);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        save(plan);
        return plan;
    }

    public IPage<UserPlan> listPlans(String userId, int pageNum, int pageSize) {
        LambdaQueryWrapper<UserPlan> wrapper = new LambdaQueryWrapper<UserPlan>()
                .eq(UserPlan::getUserId, userId)
                .orderByDesc(UserPlan::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    public UserPlan getPlan(String planId, String userId) {
        UserPlan plan = getById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "方案不存在: " + planId);
        }
        if (!plan.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权访问该方案");
        }
        return plan;
    }

    public void updatePlan(String planId, String userId, PlanCreateRequest request) {
        UserPlan plan = getPlan(planId, userId);
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setSourceText(request.getSourceText());
        if (request.getBlockChain() != null) {
            plan.setBlockChain(request.getBlockChain());
        }
        if (request.getTags() != null) {
            plan.setTags(request.getTags());
        }
        plan.setUpdateTime(LocalDateTime.now());
        updateById(plan);
    }

    public void removePlan(String planId, String userId) {
        UserPlan plan = getPlan(planId, userId);
        removeById(plan.getId());
    }

    /**
     * 从官方方案库克隆一份到当前用户的方案库
     *
     * <p>克隆时携带 sourceType="preset" 和 sourcePresetId，以便前端展示「源自官方」徽标。
     * 同时把官方方案的 useCount 自增 1，方便平台统计热度。
     */
    public UserPlan createFromPreset(String userId, String presetId) {
        PresetPackage preset = presetPackageService.getById(presetId);
        if (preset == null || !Boolean.TRUE.equals(preset.getActive())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "官方方案不存在或已下架: " + presetId);
        }

        UserPlan plan = new UserPlan();
        plan.setUserId(userId);
        plan.setName(preset.getNameZh());
        plan.setDescription(preset.getDescription());
        // 深拷贝积木链与标签，避免后续修改用户方案影响官方原模板
        plan.setBlockChain(preset.getBlockChain() != null
                ? new ArrayList<>(preset.getBlockChain()) : null);
        plan.setTags(preset.getTags() != null
                ? new ArrayList<>(preset.getTags()) : null);
        plan.setSourceType("preset");
        plan.setSourcePresetId(preset.getId());
        plan.setPublicFlag(false);
        plan.setUseCount(0);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());
        save(plan);

        // 官方模板使用次数 +1（best-effort，失败不影响主流程）
        try {
            preset.setUseCount((preset.getUseCount() == null ? 0 : preset.getUseCount()) + 1);
            presetPackageService.updateById(preset);
        } catch (Exception ignored) { /* 统计失败不阻塞 */ }

        return plan;
    }
}
