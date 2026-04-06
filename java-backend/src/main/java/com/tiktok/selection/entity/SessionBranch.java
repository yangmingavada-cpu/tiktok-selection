package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话分支实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "session_branch", schema = "db_session", autoResultMap = true)
public class SessionBranch {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String sessionId;
    private String branchId;
    private Integer parentStepSeq;
    private String triggerText;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> blockChain;

    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> mergedProducts;

    private LocalDateTime createTime;
}
