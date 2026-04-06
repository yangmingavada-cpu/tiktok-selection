package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 积木注册表实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "block_registry", schema = "db_platform", autoResultMap = true)
public class BlockRegistry {

    @TableId(value = "block_id", type = IdType.INPUT)
    private String blockId;

    private String blockType;
    private String nameZh;
    private String nameEn;
    private String description;
    private String inputType;
    private String outputType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configSchema;

    private String echotikApi;
    @TableField("is_llm_required")
    private Boolean llmRequired;
    private String estimatedCost;
    @TableField("is_active")
    private Boolean active;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
