package com.tiktok.selection.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@Builder
public class LoginResponse {
    private String token;
    private String userId;
    private String email;
    private String name;
    private String role;
    private String tierName;
}
