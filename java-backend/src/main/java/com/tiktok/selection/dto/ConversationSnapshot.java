package com.tiktok.selection.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

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
         * 方案解读文本
         */
        private String interpretation;

        /**
         * 方案预览状态
         */
        private PreviewStatus preview;

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
