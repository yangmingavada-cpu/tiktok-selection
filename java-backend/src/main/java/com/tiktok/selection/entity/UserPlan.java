package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户方案实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "user_plan", schema = "db_core", autoResultMap = true)
public class UserPlan {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String name;
    private String description;
    private String sourceText;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> blockChain;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> variableParams;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> tags;

    private String sourceType;
    private String sourcePresetId;
    private String sourceUserPlanId;
    @TableField("is_public")
    private Boolean publicFlag;
    private Integer useCount;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private LocalDateTime deleteTime;
}
