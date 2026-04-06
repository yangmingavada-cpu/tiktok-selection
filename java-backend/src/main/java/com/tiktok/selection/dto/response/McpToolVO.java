package com.tiktok.selection.dto.response;

import java.time.LocalDateTime;

/**
 * MCP工具管理视图对象
 *
 * @param toolName   工具唯一名称
 * @param tag        标签（echotik / custom / amazon 等）
 * @param enabled    是否启用
 * @param banReason  禁用原因（启用时为null）
 * @param updatedBy  最后操作人
 * @param updatedAt  最后操作时间
 * @param registered 是否已部署（存在对应Spring Bean）
 *
 * @author system
 * @date 2026/04/01
 */
public record McpToolVO(
        String toolName,
        String tag,
        boolean enabled,
        String banReason,
        String updatedBy,
        LocalDateTime updatedAt,
        boolean registered
) {}
