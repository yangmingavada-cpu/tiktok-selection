package com.tiktok.selection.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * LLM配置视图对象（返回给前端，apiKey脱敏）
 *
 * @author system
 * @date 2026/03/24
 */
@Data
@Builder
public class LlmConfigVO {

    private String id;
    private String name;
    private String provider;
    private String baseUrl;

    /**
     * API Key脱敏显示，格式：****xxxx（后4位明文），用于前端确认当前存储的是哪个Key。
     * 若解密失败（如密钥未配置）则显示 "****????"。
     */
    private String apiKeyMasked;

    private String model;
    private Integer maxTokens;
    private Boolean active;
    private Integer priority;
    private Long monthlyTokenLimit;
    private Long monthlyTokensUsed;
    private Map<String, Object> configExtra;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
