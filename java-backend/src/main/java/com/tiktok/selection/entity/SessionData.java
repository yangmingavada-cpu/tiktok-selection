package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话数据实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "session_data", schema = "db_session", autoResultMap = true)
public class SessionData {

    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> currentView;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> candidatePool;

    private LocalDateTime updateTime;
}
