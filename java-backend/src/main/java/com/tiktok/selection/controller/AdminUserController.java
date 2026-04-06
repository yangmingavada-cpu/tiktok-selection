package com.tiktok.selection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tiktok.selection.common.DesensitizeUtil;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.request.AdminUpdateUserRequest;
import com.tiktok.selection.dto.response.AdminUserListResponse;
import com.tiktok.selection.entity.User;
import com.tiktok.selection.entity.UserTier;
import com.tiktok.selection.service.UserService;
import com.tiktok.selection.service.UserTierService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员用户管理控制器
 *
 * @author system
 * @date 2026/03/22
 */
@Validated
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final UserTierService userTierService;

    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {

        IPage<User> page = userService.pageUsers(pageNum, pageSize, status, role, keyword);

        // 只查当前页用户实际关联的 tier，避免全表扫描
        Set<String> tierIds = page.getRecords().stream()
                .map(User::getTierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, UserTier> tierMap = tierIds.isEmpty() ? Map.of() :
                userTierService.listByIds(tierIds).stream()
                        .collect(Collectors.toMap(UserTier::getId, t -> t, (a, b) -> a));

        List<AdminUserListResponse> records = page.getRecords().stream().map(user -> {
            UserTier tier = tierMap.get(user.getTierId());
            return AdminUserListResponse.builder()
                    .id(user.getId())
                    .email(DesensitizeUtil.maskEmail(user.getEmail()))
                    .name(user.getName())
                    .avatarUrl(user.getAvatarUrl())
                    .role(user.getRole())
                    .status(user.getStatus())
                    .tierId(user.getTierId())
                    .tierName(tier != null ? tier.getName() : null)
                    .tierDisplayName(tier != null ? tier.getDisplayName() : null)
                    .createTime(user.getCreateTime())
                    .lastLoginTime(user.getLastLoginTime())
                    .build();
        }).toList();

        Map<String, Object> result = HashMap.newHashMap(4);
        result.put("records", records);
        result.put("total", page.getTotal());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());

        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<AdminUserListResponse> get(@PathVariable String id) {
        User user = userService.getById(id);
        if (user == null) {
            return R.fail(ErrorCode.PARAM_ERROR, "用户不存在");
        }

        UserTier tier = userTierService.getById(user.getTierId());
        return R.ok(AdminUserListResponse.builder()
                .id(user.getId())
                .email(DesensitizeUtil.maskEmail(user.getEmail()))
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .tierId(user.getTierId())
                .tierName(tier != null ? tier.getName() : null)
                .tierDisplayName(tier != null ? tier.getDisplayName() : null)
                .createTime(user.getCreateTime())
                .lastLoginTime(user.getLastLoginTime())
                .build());
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @Valid @RequestBody AdminUpdateUserRequest request) {
        User user = userService.getById(id);
        if (user == null) {
            return R.fail(ErrorCode.PARAM_ERROR, "用户不存在");
        }

        if (StringUtils.hasText(request.getStatus())) {
            user.setStatus(request.getStatus());
        }
        if (StringUtils.hasText(request.getTierId())) {
            UserTier tier = userTierService.getById(request.getTierId());
            if (tier == null) {
                return R.fail(ErrorCode.PARAM_ERROR, "等级不存在");
            }
            user.setTierId(request.getTierId());
        }
        if (StringUtils.hasText(request.getRole())) {
            user.setRole(request.getRole());
        }

        userService.updateById(user);
        return R.ok();
    }
}
