package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户等级实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "user_tier", schema = "db_core", autoResultMap = true)
public class UserTier {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private String displayName;
    private Integer monthlyApiQuota;
    private Long monthlyTokenQuota;
    private Integer maxConcurrentSessions;
    private Integer maxApiPerSession;
    private Long maxTokenPerSession;
    private Integer maxProductsPerQuery;
    private Integer maxSavedPlans;
    private BigDecimal priceMonthly;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> features;

    private Integer sortOrder;
    @TableField("is_active")
    private Boolean active;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
