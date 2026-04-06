package com.tiktok.selection.controller;

import com.tiktok.selection.common.DesensitizeUtil;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.response.UserProfileResponse;
import com.tiktok.selection.entity.User;
import com.tiktok.selection.entity.UserTier;
import com.tiktok.selection.service.UserService;
import com.tiktok.selection.service.UserTierService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器，提供用户个人信息接口
 *
 * @author system
 * @date 2026/03/22
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserTierService userTierService;

    @GetMapping("/profile")
    public R<UserProfileResponse> getProfile(Authentication authentication) {
        String userId = authentication.getPrincipal().toString();
        User user = userService.getById(userId);
        if (user == null) {
            return R.fail(ErrorCode.PARAM_ERROR, "用户不存在");
        }

        UserTier tier = userTierService.getById(user.getTierId());

        return R.ok(UserProfileResponse.builder()
                .id(user.getId())
                .email(DesensitizeUtil.maskEmail(user.getEmail()))
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .tierName(tier != null ? tier.getName() : null)
                .tierDisplayName(tier != null ? tier.getDisplayName() : null)
                .createTime(user.getCreateTime())
                .lastLoginTime(user.getLastLoginTime())
                .build());
    }
}
