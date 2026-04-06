package com.tiktok.selection.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员用户列表响应DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@Builder
public class AdminUserListResponse {
    private String id;
    private String email;
    private String name;
    private String avatarUrl;
    private String role;
    private String status;
    private String tierId;
    private String tierName;
    private String tierDisplayName;
    private LocalDateTime createTime;
    private LocalDateTime lastLoginTime;
}
