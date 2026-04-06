package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 意图解析日志实体：记录每次规划阶段的 Token 消耗，用于配额统计和看板展示
 *
 * @author system
 * @date 2026/03/24
 */
@Data
@TableName(value = "intent_parse_log", schema = "db_session")
public class IntentParseLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起规划的用户ID */
    private String userId;

    /** 对应的构建会话ID（与SSE进度流关联） */
    private String buildSessionId;

    /** 本次规划消耗的 LLM Token 总数 */
    private Integer llmTokensUsed;

    /** AI ReAct 迭代轮次（= 实际 LLM API 调用次数） */
    private Integer iterations;

    /** 本次规划消耗的 API 调用次数（每轮迭代调用一次 LLM，故等于 iterations，最少计 1） */
    private Integer apiCalls;

    /** 是否成功生成积木链 */
    private Boolean success;

    /** 用户本轮输入文本 */
    private String userText;

    /** 截至本次调用的完整 Q&A 历史 JSON */
    private String qaHistory;

    /** AI 响应类型：block_chain / needs_input / error */
    private String responseType;

    /** AI 回复内容（ask_user 问题或方案 summary） */
    private String responseMessage;

    /** 生成的积木链 JSON（成功时） */
    private String blockChain;

    /** ask_user 选项列表 JSON（needs_input 时） */
    private String suggestions;

    private LocalDateTime createTime;
}
