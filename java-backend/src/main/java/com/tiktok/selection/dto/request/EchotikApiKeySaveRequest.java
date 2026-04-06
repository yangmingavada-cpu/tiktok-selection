package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 管理员创建/更新 Echotik API 密钥请求体
 *
 * <p>apiKey 和 apiSecret 明文传入（HTTPS传输），服务层加密存库。
 * 更新时留空则保留原有密钥值。
 *
 * @author system
 * @date 2026/03/24
 */
@Data
public class EchotikApiKeySaveRequest {

    @NotBlank(message = "密钥名称不能为空")
    @Size(max = 64, message = "密钥名称最长64位")
    private String name;

    /** API Key 明文，创建时必填；更新时留空则保留原值 */
    @Size(max = 256, message = "apiKey最长256位")
    private String apiKey;

    /** API Secret 明文，创建时必填；更新时留空则保留原值 */
    @Size(max = 512, message = "apiSecret最长512位")
    private String apiSecret;

    /** 总调用次数上限，null=不限制 */
    @Min(value = 0, message = "调用次数不能为负数")
    private Integer totalCalls;

    /** 剩余调用次数，新建时通常与totalCalls相同 */
    @Min(value = 0, message = "剩余次数不能为负数")
    private Integer remainingCalls;

    /** 剩余次数告警阈值 */
    @Min(value = 0, message = "告警阈值不能为负数")
    private Integer alertThreshold;

    private Boolean active;
}
