package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.request.IntentParseRequest;
import com.tiktok.selection.dto.response.IntentParseResponse;
import com.tiktok.selection.dto.response.IntentPreviewResponse;
import com.tiktok.selection.mcp.IntentProgressBus;
import com.tiktok.selection.service.IntentPreviewService;
import com.tiktok.selection.service.IntentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 意图解析控制器
 * 前端通过此接口将用户自然语言转换为积木链
 *
 * @author system
 * @date 2026/03/24
 */
@RestController
@RequestMapping("/api/intent")
@RequiredArgsConstructor
public class IntentController {

    private static final String BLOCK_CHAIN_KEY = "block_chain";
    private static final String NEEDS_INPUT     = "needs_input";

    private final IntentService intentService;
    private final IntentPreviewService intentPreviewService;
    private final IntentProgressBus progressBus;

    /**
     * 解析用户意图，返回积木链
     * 前端使用：新建选品 → 用户输入文本 → 调此接口 → 前端预览积木链 → 用户确认 → 创建Session执行
     *
     * @param request 包含userText、可选sessionContext和可选buildSessionId
     * @return 积木链或增量操作
     */
    @PostMapping("/parse")
    @SuppressWarnings("unchecked")
    public R<IntentParseResponse> parseIntent(@Valid @RequestBody IntentParseRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null ? auth.getPrincipal().toString() : "anonymous";

        Map<String, Object> result = intentService.parseIntent(
                request.getUserText(),
                request.getSessionContext(),
                request.getBuildSessionId(),
                request.getAgentThreadId(),
                request.getConversationSummary(),
                request.getQaHistory(),
                userId);

        IntentParseResponse response = new IntentParseResponse();
        response.setSuccess(Boolean.TRUE.equals(result.get("success")));
        response.setMessage((String) result.get("message"));
        response.setAgentThreadId((String) result.get("agent_thread_id"));
        response.setConversationSummary((String) result.get("conversation_summary"));

        String resultType = (String) result.get("type");
        if (NEEDS_INPUT.equals(resultType)) {
            response.setType(NEEDS_INPUT);
            Object suggestionsObj = result.get("suggestions");
            if (suggestionsObj instanceof List<?> list) {
                response.setSuggestions(list.stream()
                        .filter(String.class::isInstance).map(String.class::cast).toList());
            }
            Object partialObj = result.get("partialBlockChain");
            if (partialObj instanceof List<?> list) {
                response.setPartialBlockChain(list.stream()
                        .filter(Map.class::isInstance)
                        .map(m -> (Map<String, Object>) m).toList());
            }
        } else if ("plan_draft".equals(resultType)) {
            response.setType("plan_draft");
            Object planObj = result.get("plan");
            if (planObj instanceof Map<?, ?> map) {
                response.setPlan((Map<String, Object>) map);
            }
        } else {
            Object blockChainObj = result.get(BLOCK_CHAIN_KEY);
            if (blockChainObj instanceof List<?> list) {
                response.setBlockChain((List<Map<String, Object>>) list);
                response.setType(BLOCK_CHAIN_KEY);
            }

            Object actionObj = result.get("action");
            if (actionObj instanceof Map<?, ?> map) {
                response.setAction((Map<String, Object>) map);
                response.setType("action");
            }

            if (response.getType() == null) {
                response.setType(BLOCK_CHAIN_KEY);
            }
        }

        response.setSummary((String) result.get("summary"));

        Object tokenObj = result.get("llm_tokens_used");
        if (tokenObj instanceof Number n) {
            response.setLlmTokensUsed(n.intValue());
        }

        Object iterObj = result.get("iterations");
        if (iterObj instanceof Number n) {
            response.setIterations(n.intValue());
        }

        return R.ok(response);
    }

    /**
     * 用户确认规划草稿后触发第二轮 Agent，开始构建积木链。
     * 前端在收到 type=plan_draft 后展示确认界面，用户点确认时调此接口。
     *
     * @param body 包含 agentThreadId、buildSessionId（可选）、conversationSummary（可选）
     * @return 同 /parse，type=block_chain 或 needs_input
     */
    @PostMapping("/confirm-plan")
    @SuppressWarnings("unchecked")
    public R<IntentParseResponse> confirmPlan(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null ? auth.getPrincipal().toString() : "anonymous";

        String agentThreadId       = body.get("agentThreadId")       instanceof String s ? s : null;
        String buildSessionId      = body.get("buildSessionId")      instanceof String s ? s : null;
        String conversationSummary = body.get("conversationSummary") instanceof String s ? s : null;
        String originalUserText    = body.get("userText")            instanceof String s ? s : null;
        List<Map<String, String>> qaHistory = body.get("qaHistory") instanceof List<?> l
                ? l.stream().filter(Map.class::isInstance)
                            .map(m -> (Map<String, String>) m).toList()
                : null;
        Map<String, Object> plan = body.get("plan") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;

        String confirmText = buildConfirmText(originalUserText, plan);

        Map<String, Object> result = intentService.parseIntent(
                confirmText,
                null,
                buildSessionId,
                agentThreadId,
                conversationSummary,
                qaHistory,
                userId);

        IntentParseResponse response = new IntentParseResponse();
        response.setSuccess(Boolean.TRUE.equals(result.get("success")));
        response.setMessage((String) result.get("message"));
        response.setAgentThreadId((String) result.get("agent_thread_id"));
        response.setConversationSummary((String) result.get("conversation_summary"));

        String resultType = (String) result.get("type");
        if (NEEDS_INPUT.equals(resultType)) {
            response.setType(NEEDS_INPUT);
            Object suggestionsObj = result.get("suggestions");
            if (suggestionsObj instanceof List<?> list) {
                response.setSuggestions(list.stream()
                        .filter(String.class::isInstance).map(String.class::cast).toList());
            }
        } else {
            Object blockChainObj = result.get(BLOCK_CHAIN_KEY);
            if (blockChainObj instanceof List<?> list) {
                response.setBlockChain((List<Map<String, Object>>) list);
            }
            response.setType(BLOCK_CHAIN_KEY);
        }
        response.setSummary((String) result.get("summary"));
        return R.ok(response);
    }

    private String buildConfirmText(String originalUserText, Map<String, Object> plan) {
        if (plan == null) {
            return "用户已确认规划方案，请继续构建积木链。";
        }
        return String.format(
                "用户已确认以下选品规划，请直接调用工具构建积木链，无需再询问任何问题：%n" +
                "目标市场：%s%n商品品类：%s%n价格区间：%s%n推荐数量：%s%n" +
                "筛选条件：%s%n评分维度：%s%n策略备注：%s%n原始需求：%s",
                plan.getOrDefault("market", ""),
                plan.getOrDefault("category", ""),
                plan.getOrDefault("price_range", ""),
                plan.getOrDefault("output_count", 20),
                plan.getOrDefault("filters", "无"),
                plan.getOrDefault("scoring_dimensions", "无"),
                plan.getOrDefault("strategy_notes", "无"),
                originalUserText != null ? originalUserText : "");
    }

    /**
     * 将积木链解读为用户友好的 Markdown 方案报告（异步，前端拿到方案后调用）。
     *
     * @param body 包含 blockChain 字段的 JSON 对象
     * @return 包含 interpretation（Markdown文本）的解读结果
     */
    @PostMapping("/interpret")
    @SuppressWarnings("unchecked")
    public R<Map<String, Object>> interpretBlockChain(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> blockChain = new ArrayList<>();
        Object raw = body.get("blockChain");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    blockChain.add((Map<String, Object>) m);
                }
            }
        }
        return R.ok(intentService.interpretBlockChain(blockChain));
    }

    /**
     * 流式解读积木链：逐 token 返回 SSE 事件，前端实时渲染 Markdown。
     */
    @PostMapping(value = "/interpret-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SuppressWarnings("unchecked")
    public SseEmitter interpretStream(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> blockChain = new ArrayList<>();
        Object raw = body.get("blockChain");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    blockChain.add((Map<String, Object>) m);
                }
            }
        }
        String userId    = body.get("userId")    instanceof String s ? s : null;
        String sessionId = body.get("sessionId") instanceof String s ? s : null;
        return intentService.interpretBlockChainStream(blockChain, userId, sessionId);
    }

    /**
     * 积木链数据预览：对第一个 DS Block 发起小规模试查，验证筛选条件是否有数据。
     * 在用户确认积木链之前调用，消耗 1 次 Echotik API 配额。
     *
     * @param body 包含 blockChain 字段的 JSON 对象
     * @return 预览结果（hasData/sampleCount/status/message）
     */
    @PostMapping("/preview")
    @SuppressWarnings("unchecked")
    public R<IntentPreviewResponse> previewIntent(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> blockChain = new ArrayList<>();
        Object raw = body.get("blockChain");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    blockChain.add((Map<String, Object>) m);
                }
            }
        }
        return R.ok(intentPreviewService.preview(blockChain));
    }

    /**
     * SSE进度流：前端订阅后实时接收AI构建步骤事件
     * 事件类型：step（每个积木块操作）、done（构建完成）、error（失败）
     *
     * @param sessionId 前端生成的构建会话ID（与parse请求中的buildSessionId一致）
     * @return SseEmitter
     */
    @GetMapping(value = "/progress/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter progressStream(@PathVariable String sessionId) {
        return progressBus.subscribe(sessionId);
    }
}
