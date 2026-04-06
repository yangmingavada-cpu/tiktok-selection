package com.tiktok.selection.dto.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 意图解析响应DTO
 *
 * @author system
 * @date 2026/03/24
 */
@Data
public class IntentParseResponse {

    /** 解析是否成功 */
    private boolean success;

    /** 结果类型: block_chain（新建）或 action（增量） */
    private String type;

    /** Python 侧规划线程ID（压缩后可能与 buildSessionId 不同） */
    private String agentThreadId;

    /** 完整积木链JSON（新建时） */
    private List<Map<String, Object>> blockChain;

    /** 增量操作描述 */
    private Map<String, Object> action;

    /** 积木链说明 */
    private String summary;

    /** 当前规划线程的压缩摘要 */
    private String conversationSummary;

    /** 不确定的地方（如品类映射模糊） */
    private List<String> ambiguities;

    /** LLM Token用量 */
    private Integer llmTokensUsed;

    /** ReAct循环次数 */
    private Integer iterations;

    /** 错误信息 */
    private String message;

    /** ask_user 时给用户的建议选项 */
    private List<String> suggestions;

    /** ask_user 时已构建的积木链（下次增量继续用） */
    private List<Map<String, Object>> partialBlockChain;

    /** create_plan 时返回的规划草稿（供前端展示确认） */
    private Map<String, Object> plan;
}
