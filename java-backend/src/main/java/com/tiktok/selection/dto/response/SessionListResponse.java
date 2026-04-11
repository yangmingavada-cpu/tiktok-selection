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

    /**
     * 对话历史独立标题，NULL 时前端 fallback 到 title
     */
    private String chatTitle;

    /**
     * LangGraph 对话线程ID
     */
    private String agentThreadId;

    private String status;

    private String sourceType;

    private Integer currentStep;

    private Integer echotikApiCalls;

    private Long llmTotalTokens;

    private LocalDateTime startTime;

    private LocalDateTime completeTime;

    private LocalDateTime createTime;
}
