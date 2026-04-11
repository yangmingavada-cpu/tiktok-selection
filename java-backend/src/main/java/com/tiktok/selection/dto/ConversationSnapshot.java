package com.tiktok.selection.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 多智能体会话快照 DTO
 * 用于在 PostgreSQL 中持久化完整的对话记录
 *
 * @author AI System
 * @since 2026-04-02
 */
@Data
public class ConversationSnapshot {

    /**
     * 对话消息列表
     */
    private List<ChatMessage> messages;

    /**
     * QA 历史（规划阶段的问答记录）
     */
    private List<QaEntry> qaHistory;

    /**
     * 规划摘要文本
     */
    private String planningSummary;

    /**
     * 快照保存时间
     */
    private LocalDateTime savedAt;

    /**
     * 聊天消息
     * 后端只是 store-and-forward，所有复杂结构（plan / planDraft / planningSnapshot）
     * 用 Object 透传，由前端定义实际 schema
     */
    @Data
    public static class ChatMessage {
        /**
         * 角色：user | ai
         */
        private String role;

        /**
         * 消息文本内容
         */
        private String text;

        /**
         * 方案（blockChain）
         */
        private Object plan;

        /**
         * 方案解读文本（流式生成完成后的最终 markdown）
         */
        private String interpretation;

        /**
         * 方案解读流式完成标志，控制方案卡片的"确认创建"按钮 disabled
         */
        private Boolean interpretationDone;

        /**
         * 方案预览状态
         */
        private PreviewStatus preview;

        /**
         * 方案卡片的兜底摘要（interpretation 缺失时显示）
         */
        private String summary;

        /**
         * 用户原始需求文本（confirmPlan 时透传，用于"重新调整"按钮）
         */
        private String sourceText;

        /**
         * 方案建议数组
         */
        private List<String> suggestions;

        /**
         * 规划进度气泡快照
         * 结构：{status, sessionId, thinkingText, steps[], traceEntries[]}
         */
        private Object planningSnapshot;

        /**
         * 规划草稿卡片（PlanDraft 结构）
         */
        private Object planDraft;

        /**
         * 执行卡片引用 — 不存 rows/steps，只存 sessionId
         * 恢复时按这个引用拉对应 session 的 currentView + steps 重建 ExecSession
         * 结构：{sessionId: "xxx"}
         */
        private Map<String, String> execCardRef;

        /**
         * 消息时间戳
         */
        private LocalDateTime timestamp;
    }

    /**
     * QA 条目
     */
    @Data
    public static class QaEntry {
        /**
         * 问题
         */
        private String q;

        /**
         * 答案
         */
        private String a;
    }

    /**
     * 预览状态
     */
    @Data
    public static class PreviewStatus {
        /**
         * 状态：ok | empty | error | loading
         */
        private String status;

        /**
         * 状态消息
         */
        private String message;
    }
}
