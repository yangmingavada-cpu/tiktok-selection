package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM配置实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "llm_config", schema = "db_platform", autoResultMap = true)
public class LlmConfig {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String provider;
    private String baseUrl;
    private String apiKeyEncrypted;
    private String model;
    private Integer maxTokens;
    @TableField("is_active")
    private Boolean active;
    private Integer priority;
    private Long monthlyTokenLimit;
    private Long monthlyTokensUsed;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configExtra;

    private Integer contextWindow;
    private Integer compactMessageLimit;
    private Integer compactCharLimit;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
