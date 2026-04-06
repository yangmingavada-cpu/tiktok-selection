package com.tiktok.selection.mcp;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool执行后返回的Observation结构
 * 是Agent做下一步决策的唯一依据
 *
 * @author system
 * @date 2026/03/24
 */
@Data
@Builder
public class McpObservation {

    /** 执行是否成功 */
    private Boolean success;

    /** 人可读的执行结果描述 */
    private String message;

    /** 当前积木链长度 */
    private Integer chainLength;

    /** 当前数据流的实体类型 */
    private String dataType;

    /** 当前可用字段列表（下一步Tool的field enum来源） */
    private List<String> availableFields;

    /** 估算数据行数 */
    private Integer estimatedRows;

    /** 已有的评分字段列表 */
    private List<String> scoreFields;

    /** 当前成本估算 */
    private Map<String, Object> costEstimate;

    /** 对Agent下一步的建议 */
    private String hint;

    /** 如果失败，错误原因 */
    private String error;

    /** finalize_chain时返回完整积木链 */
    private List<Map<String, Object>> blockChain;

    /** ask_user时标识需要用户输入（值为 "needs_input"） */
    private String type;

    /** ask_user时给用户的建议选项列表 */
    private List<String> suggestions;

    /**
     * 积木链快照：每次工具成功执行后注入当前完整积木链。
     * Python Agent 将此快照存入 LangGraph checkpoint，
     * 服务重启或 Redis ChainBuildSession 丢失时可凭此恢复构建状态。
     */
    private List<Map<String, Object>> chainSnapshot;

    /**
     * create_plan 工具返回的规划方案草稿（SelectionPlan 结构）。
     * Python Agent 读取后以 type="plan_draft" 返回给前端展示确认。
     */
    private Map<String, Object> selectionPlan;
}
