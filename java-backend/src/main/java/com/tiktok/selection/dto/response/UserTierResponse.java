package com.tiktok.selection.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户等级响应DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@Builder
public class UserTierResponse {

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
    private Map<String, Object> features;
    private Integer sortOrder;
    private Boolean active;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
