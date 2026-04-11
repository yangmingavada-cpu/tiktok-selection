package com.tiktok.selection.dto.response;

import com.tiktok.selection.dto.ConversationSnapshot;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话详情响应DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class SessionResponse {

    private String id;

    private String userId;

    private String title;

    private String status;

    private Integer currentStep;

    private String sourceType;

    private String sourceText;

    private String sourcePlanId;

    private String matchedPreset;

    private List<Map<String, Object>> blockChain;

    private Integer echotikApiCalls;

    private Long llmTotalTokens;

    private LocalDateTime startTime;

    private LocalDateTime completeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 当前视图数据，来源于SessionData
     */
    private Map<String, Object> currentView;

    /**
     * 用户在前端添加的额外列与值
     * 结构：{ cols: [{id,label,type,options}], values: { rowIdx: {colId: value} } }
     */
    private Map<String, Object> userExtraCols;

    /**
     * 已执行步骤总数
     */
    private Integer stepCount;

    /**
     * 会话对话快照（用于前端恢复对话）
     */
    private ConversationSnapshot conversationSnapshot;

    /**
     * 执行前审计结果
     */
    private Map<String, Object> auditResult;

    /**
     * 竞品洞察分析报告（Markdown）
     */
    private String competitorAnalysis;

    /**
     * 用户备注信息
     */
    private String remark;
}
