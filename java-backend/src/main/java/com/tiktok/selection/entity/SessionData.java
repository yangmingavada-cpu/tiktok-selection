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

    /**
     * 用户在前端手动添加的列与值
     * 结构：{ cols: [{id,label,type,options}], values: { rowIdx: {colId: value} } }
     * 与 currentView 完全隔离，不会被 SSE step_complete 覆盖。
     */
    @TableField(value = "user_extra_cols", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> userExtraCols;

    private LocalDateTime updateTime;
}
