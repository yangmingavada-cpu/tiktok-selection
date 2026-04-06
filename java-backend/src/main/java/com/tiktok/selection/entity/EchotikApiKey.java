package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Echotik API密钥实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "echotik_api_key", schema = "db_platform")
public class EchotikApiKey {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String apiKeyEncrypted;
    private String apiSecretEncrypted;
    private Integer totalCalls;
    private Integer remainingCalls;
    private Integer alertThreshold;
    @TableField("is_active")
    private Boolean active;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
