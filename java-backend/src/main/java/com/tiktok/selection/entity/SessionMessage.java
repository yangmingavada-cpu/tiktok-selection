package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话消息实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "session_message", schema = "db_session", autoResultMap = true)
public class SessionMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private String role;
    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parsedAction;

    private Integer llmTokensUsed;
    private LocalDateTime createTime;
}
