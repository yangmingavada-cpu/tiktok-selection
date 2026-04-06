package com.tiktok.selection.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Echotik API 密钥脱敏视图对象
 *
 * <p>apiKey 和 apiSecret 仅返回后4位（格式 ****xxxx），原文不出网。
 *
 * @author system
 * @date 2026/03/24
 */
@Data
@Builder
public class EchotikApiKeyVO {

    private String id;
    private String name;
    /** 脱敏后的 API Key（格式：****xxxx） */
    private String apiKeyMasked;
    /** 脱敏后的 API Secret（格式：****xxxx） */
    private String apiSecretMasked;
    private Integer totalCalls;
    private Integer remainingCalls;
    private Integer alertThreshold;
    /** 剩余次数是否低于告警阈值 */
    private Boolean belowThreshold;
    private Boolean active;
    private LocalDateTime lastUsedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
