package com.tiktok.selection.mcp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;

/**
 * MCP积木链构建会话状态，存储在Redis中
 * 生命周期：一次/intent/parse请求，结束后销毁
 *
 * @author system
 * @date 2026/03/24
 */
@Data
public class ChainBuildSession {

    /** 构建会话ID（等同于Python传来的session_id） */
    private String sessionId;

    /** 发起规划的用户ID，用于配额/限流校验 */
    private String userId;

    /** 当前积木链（已添加的块） */
    private List<Map<String, Object>> blocks = new ArrayList<>();

    /** 当前数据流的实体类型：product_list, influencer_list, video_list, etc. */
    private String currentOutputType;

    /** 当前可用字段列表（动态enum的来源） */
    private List<String> availableFields = new ArrayList<>();

    /** 已添加的评分字段 */
    private List<String> scoreFields = new ArrayList<>();

    /** 是否已选择数据源 */
    private boolean hasDataSource = false;

    /** 估算数据行数 */
    private int estimatedRowCount = 0;

    /** 步骤序号计数器 */
    private int seqCounter = 0;

    /** 是否为增量模式 */
    private boolean incrementalMode = false;

    /** 幂等性保护：已处理的tool_call_id集合 */
    private Set<String> processedToolCallIds = new HashSet<>();

    /** create_plan 工具提交的选品规划草稿（等待用户确认） */
    private Map<String, Object> selectionPlan;

    /** 创建时间 */
    private String createdAt = LocalDateTime.now().toString();
}
