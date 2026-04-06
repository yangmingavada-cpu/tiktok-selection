package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP工具配置实体（管理员可按工具名或标签批量启用/禁用）
 *
 * <p>DDL（db_platform schema）：
 * <pre>
 * CREATE TABLE mcp_tool_config (
 *     id          VARCHAR(36)  PRIMARY KEY,
 *     tool_name   VARCHAR(64)  NOT NULL UNIQUE,
 *     is_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
 *     ban_reason  VARCHAR(255),
 *     updated_by  VARCHAR(64),
 *     updated_at  TIMESTAMP    NOT NULL,
 *     created_at  TIMESTAMP    NOT NULL
 * );
 * </pre>
 *
 * @author system
 * @date 2026/04/01
 */
@Data
@TableName(value = "mcp_tool_config", schema = "db_platform")
public class McpToolConfig {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String toolName;

    @TableField("is_enabled")
    private Boolean enabled;

    /** 是否为敏感工具（仅付费 Tier 可用），默认 false */
    @TableField("sensitive_flag")
    private Boolean sensitiveFlag;

    /** 禁用原因（启用时置null） */
    private String banReason;

    /** 最后操作人（管理员userId） */
    private String updatedBy;

    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
