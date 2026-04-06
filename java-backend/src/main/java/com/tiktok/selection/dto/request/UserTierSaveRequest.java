package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 管理员创建/更新用户等级请求体
 *
 * @author system
 * @date 2026/03/24
 */
@Data
public class UserTierSaveRequest {

    @NotBlank(message = "等级标识不能为空")
    @Size(max = 32, message = "等级标识最长32位")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "等级标识只允许小写字母、数字和下划线，且以字母开头")
    private String name;

    @NotBlank(message = "等级显示名称不能为空")
    @Size(max = 64, message = "显示名称最长64位")
    private String displayName;

    /** 每月API调用配额，null=不限制 */
    @Min(value = 0, message = "每月API配额不能为负数")
    private Integer monthlyApiQuota;

    /** 每月Token配额，null=不限制 */
    @Min(value = 0, message = "每月Token配额不能为负数")
    private Long monthlyTokenQuota;

    @Min(value = 1, message = "最大并发会话数至少为1")
    @Max(value = 100, message = "最大并发会话数不超过100")
    private Integer maxConcurrentSessions;

    @Min(value = 1, message = "每会话最大API次数至少为1")
    private Integer maxApiPerSession;

    @Min(value = 1, message = "每会话最大Token数至少为1")
    private Long maxTokenPerSession;

    @Min(value = 1, message = "每次查询最大商品数至少为1")
    @Max(value = 10000, message = "每次查询最大商品数不超过10000")
    private Integer maxProductsPerQuery;

    @Min(value = 0, message = "最大保存方案数不能为负数")
    private Integer maxSavedPlans;

    @DecimalMin(value = "0.00", message = "月价格不能为负数")
    @Digits(integer = 8, fraction = 2, message = "月价格格式不正确")
    private BigDecimal priceMonthly;

    /** 特性开关Map，最多20个键值对 */
    private Map<String, Object> features;

    @Min(value = 0, message = "排序值不能为负数")
    private Integer sortOrder;

    private Boolean active;
}
