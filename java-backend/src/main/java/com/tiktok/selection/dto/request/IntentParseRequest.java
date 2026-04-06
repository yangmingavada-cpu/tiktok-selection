package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 意图解析请求DTO
 *
 * @author system
 * @date 2026/03/24
 */
@Data
public class IntentParseRequest {

    /** 用户自然语言输入（含上下文历史，故上限调整为8000字） */
    @NotBlank(message = "用户输入不能为空")
    @Size(max = 8000, message = "输入不能超过8000字")
    private String userText;

    /**
     * 增量模式下传入的Session上下文（新建时为null）
     * 包含: blockChain, currentOutputType, availableFields, scoreFields
     */
    private Map<String, Object> sessionContext;

    /**
     * 前端生成的构建会话ID（可选）
     * 传入后可通过 GET /api/intent/progress/{buildSessionId} 订阅SSE进度推送
     */
    private String buildSessionId;

    /**
     * 规划Agent线程ID（与 buildSessionId 分离，用于会话压缩后续接 checkpoint）
     */
    private String agentThreadId;

    /**
     * 当前规划线程的压缩摘要（checkpoint 丢失或重建时使用）
     */
    private String conversationSummary;

    /**
     * ask_user 多轮问答历史（增量模式时传入，供LLM还原对话上下文）
     * 每个元素包含 q（AI提问）和 a（用户回答）
     */
    private List<Map<String, String>> qaHistory;
}
