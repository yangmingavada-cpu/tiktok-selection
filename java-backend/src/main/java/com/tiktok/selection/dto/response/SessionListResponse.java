package com.tiktok.selection.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表响应DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class SessionListResponse {

    private String id;

    private String title;

    private String status;

    private String sourceType;

    private Integer currentStep;

    private Integer echotikApiCalls;

    private Long llmTotalTokens;

    private LocalDateTime startTime;

    private LocalDateTime completeTime;

    private LocalDateTime createTime;
}
