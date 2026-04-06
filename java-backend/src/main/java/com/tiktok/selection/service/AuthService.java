package com.tiktok.selection.service;

import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.DesensitizeUtil;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.dto.request.LoginRequest;
import com.tiktok.selection.dto.response.LoginResponse;
import com.tiktok.selection.dto.request.RegisterRequest;
import com.tiktok.selection.entity.User;
import com.tiktok.selection.entity.UserTier;
import com.tiktok.selection.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务，负责用户注册与登录
 *
 * @author system
 * @date 2026/03/22
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserTierService userTierService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        if (userService.emailExists(request.getEmail())) {
            throw new BusinessException(ErrorCode.USER_NAME_ALREADY_EXISTS, "邮箱已被注册");
        }

        UserTier defaultTier = userTierService.findDefaultTier()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "默认等级未配置"));

        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setTierId(defaultTier.getId());
        user.setRole("user");
        user.setStatus("active");

        userService.save(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        // 用户敏感数据禁止直接展示，必须对展示数据进行脱敏（安全规约第2条）
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(DesensitizeUtil.maskEmail(user.getEmail()))
                .name(user.getName())
                .role(user.getRole())
                .tierName(defaultTier.getDisplayName())
                .build();
    }

    public LoginResponse login(LoginRequest request) {
        User user = userService.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_PASSWORD_ERROR, "邮箱或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR, "邮箱或密码错误");
        }

        if (!"active".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_ACCOUNT_FROZEN, "账号已被禁用");
        }

        userService.updateLastLogin(user.getId());

        UserTier tier = userTierService.getById(user.getTierId());
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .email(DesensitizeUtil.maskEmail(user.getEmail()))
                .name(user.getName())
                .role(user.getRole())
                .tierName(tier != null ? tier.getDisplayName() : "未知")
                .build();
    }
}
