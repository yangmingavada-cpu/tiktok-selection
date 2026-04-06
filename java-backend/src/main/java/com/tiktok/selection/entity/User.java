package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "user", schema = "db_core", autoResultMap = true)
public class User {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String email;
    private String name;
    private String passwordHash;
    private String avatarUrl;
    private String tierId;
    private String role;
    private String status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private LocalDateTime deleteTime;
}
