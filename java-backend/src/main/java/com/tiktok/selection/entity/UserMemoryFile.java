package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户记忆文件索引（仅存元数据，文件内容存于磁盘）
 *
 * <p>DDL（db_platform schema）：
 * <pre>
 * CREATE TABLE user_memory_files (
 *     id           VARCHAR(36)   PRIMARY KEY,
 *     user_id      VARCHAR(64)   NOT NULL,
 *     session_id   VARCHAR(64),                          -- NULL = common 跨会话记忆
 *     file_path    VARCHAR(512)  NOT NULL,               -- 相对于 memory_root 的路径
 *     memory_type  VARCHAR(20)   NOT NULL,               -- user/feedback/project/reference
 *     name         VARCHAR(128)  NOT NULL,
 *     description  VARCHAR(255),                         -- 相关性筛选摘要，不读文件即可判断
 *     agent_type   VARCHAR(64),                          -- 写入来源，多Agent并行时追溯用
 *     created_at   TIMESTAMP     NOT NULL,
 *     updated_at   TIMESTAMP     NOT NULL,
 *     UNIQUE (user_id, file_path)
 * );
 * CREATE INDEX idx_umf_user_session ON user_memory_files(user_id, session_id);
 * </pre>
 *
 * @author system
 * @date 2026/04/01
 */
@Data
@TableName(value = "user_memory_files", schema = "db_platform")
public class UserMemoryFile {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** NULL 表示 common 跨会话记忆 */
    private String sessionId;

    /** 相对于 memory_root 的文件路径，如 {userId}/common/user_preferences.md */
    private String filePath;

    /** user / feedback / project / reference */
    private String memoryType;

    private String name;

    /** 一句话摘要，用于 AI 相关性筛选（避免读全文）*/
    private String description;

    /** 写入来源，为多 Agent 并行调度预留追溯字段 */
    private String agentType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
