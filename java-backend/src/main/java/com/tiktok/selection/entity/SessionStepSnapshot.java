package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话步骤快照实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "session_step_snapshot", schema = "db_session", autoResultMap = true)
public class SessionStepSnapshot {

    @TableId(value = "step_id", type = IdType.INPUT)
    private Long stepId;

    private String sessionId;
    private String snapshotType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object snapshotData;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> snapshotIds;

    private LocalDateTime createTime;
}
