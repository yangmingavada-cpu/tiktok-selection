package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 用户等级数据传输对象
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class UserTierDTO {

    @NotBlank(message = "等级标识不能为空")
    private String name;

    @NotBlank(message = "显示名称不能为空")
    private String displayName;

    @NotNull
    private Integer monthlyApiQuota;

    @NotNull
    private Long monthlyTokenQuota;

    private Integer maxConcurrentSessions;
    private Integer maxApiPerSession;
    private Long maxTokenPerSession;
    private Integer maxProductsPerQuery;
    private Integer maxSavedPlans;
    private BigDecimal priceMonthly;
    private Map<String, Object> features;
    private Integer sortOrder;
}
