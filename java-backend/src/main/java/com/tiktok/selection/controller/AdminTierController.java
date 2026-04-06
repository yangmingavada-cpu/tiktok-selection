package com.tiktok.selection.controller;

import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.request.UserTierSaveRequest;
import com.tiktok.selection.entity.UserTier;
import com.tiktok.selection.service.UserTierService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员用户等级配置接口
 *
 * <ul>
 *   <li>GET    /api/admin/tiers        — 查询所有等级（含禁用）
 *   <li>GET    /api/admin/tiers/{id}   — 查询单个等级
 *   <li>POST   /api/admin/tiers        — 新建等级
 *   <li>PUT    /api/admin/tiers/{id}   — 更新等级
 *   <li>DELETE /api/admin/tiers/{id}   — 删除等级
 * </ul>
 *
 * @author system
 * @date 2026/03/24
 */
@Validated
@RestController
@RequestMapping("/api/admin/tiers")
@RequiredArgsConstructor
public class AdminTierController {

    private static final String ID_PATTERN = "^[0-9a-zA-Z\\-]{1,64}$";
    private static final String ID_PATTERN_MSG = "id格式无效";
    /** features Map 最大键数，防超大对象入库 */
    private static final int FEATURES_MAX_KEYS = 20;

    private final UserTierService userTierService;

    @GetMapping
    public R<List<UserTier>> list() {
        return R.ok(userTierService.list());
    }

    @GetMapping("/{id}")
    public R<UserTier> get(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        UserTier tier = userTierService.getById(id);
        if (tier == null) {
            return R.fail(ErrorCode.PARAM_ERROR, "等级不存在");
        }
        return R.ok(tier);
    }

    @PostMapping
    public R<UserTier> create(@Valid @RequestBody UserTierSaveRequest req) {
        if (req.getFeatures() != null && req.getFeatures().size() > FEATURES_MAX_KEYS) {
            return R.fail(ErrorCode.PARAM_ERROR, "features最多允许" + FEATURES_MAX_KEYS + "个配置项");
        }
        // 检查 name 唯一性
        if (userTierService.findByName(req.getName()).isPresent()) {
            return R.fail(ErrorCode.PARAM_ERROR, "等级标识已存在: " + req.getName());
        }
        UserTier tier = buildEntity(req);
        userTierService.save(tier);
        return R.ok(tier);
    }

    @PutMapping("/{id}")
    public R<UserTier> update(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id,
            @Valid @RequestBody UserTierSaveRequest req) {
        if (req.getFeatures() != null && req.getFeatures().size() > FEATURES_MAX_KEYS) {
            return R.fail(ErrorCode.PARAM_ERROR, "features最多允许" + FEATURES_MAX_KEYS + "个配置项");
        }
        UserTier tier = userTierService.getById(id);
        if (tier == null) {
            return R.fail(ErrorCode.PARAM_ERROR, "等级不存在");
        }
        // name 变更时检查唯一性
        if (!req.getName().equals(tier.getName())) {
            if (userTierService.findByName(req.getName()).isPresent()) {
                return R.fail(ErrorCode.PARAM_ERROR, "等级标识已存在: " + req.getName());
            }
        }
        fillEntity(tier, req);
        userTierService.updateById(tier);
        return R.ok(tier);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        UserTier tier = userTierService.getById(id);
        if (tier == null) {
            return R.fail(ErrorCode.PARAM_ERROR, "等级不存在");
        }
        userTierService.removeById(id);
        return R.ok();
    }

    // ==================== 私有工具 ====================

    private UserTier buildEntity(UserTierSaveRequest req) {
        UserTier tier = new UserTier();
        fillEntity(tier, req);
        return tier;
    }

    private void fillEntity(UserTier tier, UserTierSaveRequest req) {
        tier.setName(req.getName());
        tier.setDisplayName(req.getDisplayName());
        tier.setMonthlyApiQuota(req.getMonthlyApiQuota());
        tier.setMonthlyTokenQuota(req.getMonthlyTokenQuota());
        tier.setMaxConcurrentSessions(req.getMaxConcurrentSessions());
        tier.setMaxApiPerSession(req.getMaxApiPerSession());
        tier.setMaxTokenPerSession(req.getMaxTokenPerSession());
        tier.setMaxProductsPerQuery(req.getMaxProductsPerQuery());
        tier.setMaxSavedPlans(req.getMaxSavedPlans());
        tier.setPriceMonthly(req.getPriceMonthly());
        tier.setFeatures(req.getFeatures());
        tier.setSortOrder(req.getSortOrder());
        if (req.getActive() != null) {
            tier.setActive(req.getActive());
        }
    }
}
