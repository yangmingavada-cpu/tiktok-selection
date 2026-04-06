package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理员更新用户请求DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class AdminUpdateUserRequest {

    @Pattern(regexp = "active|disabled|banned", message = "状态只能是 active / disabled / banned")
    private String status;

    private String tierId;

    @Pattern(regexp = "user|admin", message = "角色只能是 user / admin")
    private String role;
}
