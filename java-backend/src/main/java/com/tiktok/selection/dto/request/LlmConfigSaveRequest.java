package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Map;

/**
 * LLM配置保存请求（创建 & 更新共用）
 * 创建时 apiKey 必填；更新时 apiKey 为空表示保留原有Key。
 *
 * @author system
 * @date 2026/03/24
 */
@Data
public class LlmConfigSaveRequest {

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 64, message = "配置名称最多64个字符")
    // P2-1修复：限制安全字符，禁止换行符等控制字符，防日志注入
    @Pattern(
        regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9 _\\-().]{1,64}$",
        message = "配置名称只能包含中文、字母、数字及 _-().空格"
    )
    private String name;

    @NotBlank(message = "provider不能为空")
    @Pattern(
        regexp = "anthropic|openai|deepseek|local|qwen|zhipu|moonshot|openrouter|siliconflow",
        message = "不支持的provider，可选：anthropic/openai/deepseek/local/qwen/zhipu/moonshot/openrouter/siliconflow"
    )
    private String provider;

    @NotBlank(message = "baseUrl不能为空")
    @Size(max = 512, message = "baseUrl最多512个字符")
    // P1-1修复：限制scheme为http/https，防止SSRF（file://、javascript:等协议）
    @Pattern(
        regexp = "^https?://[^\\s]{1,500}$",
        message = "baseUrl必须以http://或https://开头，且不能包含空白字符"
    )
    private String baseUrl;

    /**
     * 明文 API Key。
     * 创建时必填；更新时留空则保留数据库中已有的加密Key。
     */
    @Size(max = 256, message = "apiKey最多256个字符")
    private String apiKey;

    @NotBlank(message = "model不能为空")
    @Size(max = 128, message = "model名称最多128个字符")
    // P2-1修复：model名只允许字母数字及.-/:，与主流LLM命名规则一致（含Ollama的model:tag格式），防日志注入
    @Pattern(
        regexp = "^[a-zA-Z0-9][a-zA-Z0-9._\\-/:]{0,127}$",
        message = "model名称只能包含字母、数字及 ._-/: 字符"
    )
    private String model;

    @Min(value = 1, message = "maxTokens最小为1")
    @Max(value = 32768, message = "maxTokens最大为32768")
    private Integer maxTokens;

    private Boolean active;

    @Min(value = 0, message = "priority最小为0")
    @Max(value = 9999, message = "priority最大为9999")
    private Integer priority;

    /**
     * 每月Token限额，-1 表示无限制。
     */
    @Min(value = -1, message = "monthlyTokenLimit最小为-1（-1表示无限制）")
    private Long monthlyTokenLimit;

    private Map<String, Object> configExtra;
}
