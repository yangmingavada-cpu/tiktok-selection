package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.request.LoginRequest;
import com.tiktok.selection.dto.response.LoginResponse;
import com.tiktok.selection.dto.request.RegisterRequest;
import com.tiktok.selection.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器，提供注册与登录接口
 *
 * @author system
 * @date 2026/03/22
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public R<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }
}
