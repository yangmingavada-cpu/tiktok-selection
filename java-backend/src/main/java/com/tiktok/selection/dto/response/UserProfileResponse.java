package com.tiktok.selection.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户个人信息响应DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@Builder
public class UserProfileResponse {
    private String id;
    private String email;
    private String name;
    private String avatarUrl;
    private String role;
    private String status;
    private String tierName;
    private String tierDisplayName;
    private LocalDateTime createTime;
    private LocalDateTime lastLoginTime;
}
