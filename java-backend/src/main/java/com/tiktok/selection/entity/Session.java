package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "session", schema = "db_session", autoResultMap = true)
public class Session {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String title;
    private String status;
    private Integer currentStep;
    private String sourceText;
    private String sourceType;
    private String sourcePlanId;
    private String matchedPreset;

    /**
     * 对话线程ID（LangGraph checkpoint 的稳定标识，整个对话生命周期唯一）
     * 用于会话级记忆的隔离键
     */
    private String agentThreadId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> blockChain;

    private Integer echotikApiCalls;
    private Long llmInputTokens;
    private Long llmOutputTokens;
    private Long llmTotalTokens;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> echotikKeyIdsUsed;

    /**
     * 多智能体会话快照（JSONB）
     * 存储完整对话记录、QA历史、规划摘要
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode conversationSnapshot;

    /**
     * 执行前审计结果（JSONB）：{pass, score, issues, suggestions}
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> auditResult;

    /**
     * 竞品洞察分析报告（Markdown 文本）
     */
    private String competitorAnalysis;

    /**
     * 用户备注信息
     */
    private String remark;

    /**
     * 是否从"对话历史"侧栏隐藏（不影响选品记录页）
     * 与 hiddenFromRecords / deleteTime 完全独立
     */
    private Boolean hiddenFromChat;

    /**
     * 是否从"选品记录"页隐藏（不影响对话历史侧栏）
     * 与 hiddenFromChat / deleteTime 完全独立
     */
    private Boolean hiddenFromRecords;

    /**
     * 对话历史独立标题，NULL 时 fallback 到 title
     * 用户在对话历史侧栏改名只动这里，不影响选品记录页 title
     */
    private String chatTitle;

    private LocalDateTime startTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private LocalDateTime deleteTime;
}
