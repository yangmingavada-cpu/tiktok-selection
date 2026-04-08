package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建会话请求DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class SessionCreateRequest {

    /**
     * 要执行的Block链
     */
    @NotNull(message = "blockChain不能为空")
    private List<Map<String, Object>> blockChain;

    /**
     * 来源类型: "preset" / "user_plan" / "llm_new"
     */
    private String sourceType;

    /**
     * 关联的用户方案ID（可选）
     */
    private String sourcePlanId;

    /**
     * 原始自然语言输入
     */
    private String sourceText;

    /**
     * 会话标题（可选）
     */
    private String title;

    /**
     * 对话线程ID（LangGraph checkpoint 标识，用于会话级记忆隔离）
     */
    private String agentThreadId;
}
