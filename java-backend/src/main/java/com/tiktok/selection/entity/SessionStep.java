package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话步骤实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "session_step", schema = "db_session", autoResultMap = true)
public class SessionStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private String branchId;
    private Integer seq;
    private String blockId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> blockConfig;

    private String label;
    private Integer inputCount;
    private Integer outputCount;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> productIds;

    private Integer echotikApiCalls;
    private Integer llmInputTokens;
    private Integer llmOutputTokens;
    private Integer llmTotalTokens;
    private String echotikKeyId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> apiEndpointsCalled;

    private Integer durationMs;
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
}
