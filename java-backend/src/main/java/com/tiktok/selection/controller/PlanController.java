package com.tiktok.selection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.request.PlanCreateRequest;
import com.tiktok.selection.dto.request.SessionCreateRequest;
import com.tiktok.selection.dto.response.PlanResponse;
import com.tiktok.selection.dto.response.SessionResponse;
import com.tiktok.selection.entity.Session;
import com.tiktok.selection.entity.UserPlan;
import com.tiktok.selection.service.SessionService;
import com.tiktok.selection.service.UserPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户方案控制器
 *
 * @author system
 * @date 2026/03/24
 */
@Validated
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final UserPlanService userPlanService;
    private final SessionService sessionService;

    @GetMapping
    public R<IPage<PlanResponse>> listPlans(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        String userId = getCurrentUserId();
        IPage<UserPlan> page = userPlanService.listPlans(userId, pageNum, pageSize);
        return R.ok(page.convert(this::toResponse));
    }

    @PostMapping
    public R<PlanResponse> createPlan(@Valid @RequestBody PlanCreateRequest request) {
        String userId = getCurrentUserId();
        UserPlan plan = userPlanService.createPlan(userId, request);
        return R.ok(toResponse(plan));
    }

    @GetMapping("/{id}")
    public R<PlanResponse> getPlan(@PathVariable String id) {
        String userId = getCurrentUserId();
        UserPlan plan = userPlanService.getPlan(id, userId);
        return R.ok(toResponse(plan));
    }

    @PutMapping("/{id}")
    public R<Void> updatePlan(@PathVariable String id, @Valid @RequestBody PlanCreateRequest request) {
        String userId = getCurrentUserId();
        userPlanService.updatePlan(id, userId, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> deletePlan(@PathVariable String id) {
        String userId = getCurrentUserId();
        userPlanService.removePlan(id, userId);
        return R.ok();
    }

    /**
     * 从方案创建并启动 Session
     */
    @PostMapping("/{id}/execute")
    public R<SessionResponse> executePlan(@PathVariable String id) {
        String userId = getCurrentUserId();
        UserPlan plan = userPlanService.getPlan(id, userId);

        SessionCreateRequest req = new SessionCreateRequest();
        req.setTitle(plan.getName());
        req.setSourceType("user_plan");
        req.setSourcePlanId(plan.getId());
        req.setSourceText(plan.getSourceText());

        if (plan.getBlockChain() != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chain = plan.getBlockChain().stream()
                    .filter(Map.class::isInstance)
                    .map(o -> (Map<String, Object>) o)
                    .toList();
            req.setBlockChain(chain);
        }

        // createSession 内部会自动根据 sourcePlanId 递增 plan.useCount + 更新 lastUsedTime
        Session session = sessionService.createSession(userId, req);

        // 启动执行
        sessionService.executeSession(session.getId(), userId);

        SessionResponse response = sessionService.getSessionDetail(session.getId(), userId);
        return R.ok(response);
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    private PlanResponse toResponse(UserPlan plan) {
        PlanResponse r = new PlanResponse();
        r.setId(plan.getId());
        r.setName(plan.getName());
        r.setDescription(plan.getDescription());
        r.setSourceText(plan.getSourceText());
        r.setBlockChain(plan.getBlockChain());
        r.setTags(plan.getTags());
        r.setUseCount(plan.getUseCount());
        r.setPublicFlag(plan.getPublicFlag());
        r.setLastUsedTime(plan.getLastUsedTime());
        r.setCreateTime(plan.getCreateTime());
        r.setUpdateTime(plan.getUpdateTime());
        return r;
    }
}
