package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.dto.request.PlanCreateRequest;
import com.tiktok.selection.entity.UserPlan;
import com.tiktok.selection.mapper.UserPlanMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户方案服务
 *
 * @author system
 * @date 2026/03/24
 */
@Service
public class UserPlanService extends ServiceImpl<UserPlanMapper, UserPlan> {

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
}
