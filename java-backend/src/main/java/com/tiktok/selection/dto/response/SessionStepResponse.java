package com.tiktok.selection.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话步骤响应DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class SessionStepResponse {

    private Long id;

    private Integer seq;

    private String blockId;

    private String label;

    private String status;

    private Integer inputCount;

    private Integer outputCount;

    private Integer echotikApiCalls;

    private Integer llmTotalTokens;

    private Integer durationMs;

    private LocalDateTime createTime;
}
