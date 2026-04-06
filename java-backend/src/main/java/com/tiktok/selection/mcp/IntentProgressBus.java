package com.tiktok.selection.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI构建进度事件总线：通过SSE向前端推送每个积木块的构建进度
 * 支持先发布后订阅（事件缓冲），解决SSE连接与parse请求的竞争问题
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class IntentProgressBus {

    private static final Logger log = LoggerFactory.getLogger(IntentProgressBus.class);

    /** SSE超时：比Python Agent最大超时(600s)多留60s余量 */
    private static final long SSE_TIMEOUT_MS = 660_000L;

    /** sessionId → 已连接的SseEmitter */
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** sessionId → 未被消费的缓冲事件（格式: [eventType, jsonData]） */
    private final ConcurrentHashMap<String, Queue<String[]>> buffers = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public IntentProgressBus(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 前端订阅进度流。若有缓冲事件则立即重放。
     *
     * @param sessionId 构建会话ID
     * @return SseEmitter 实例
     */
    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(sessionId, emitter);
        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));

        // 重放缓冲事件（处理前端订阅晚于工具调用的情况）
        Queue<String[]> buffered = buffers.remove(sessionId);
        if (buffered != null) {
            for (String[] entry : buffered) {
                doSend(emitter, entry[0], entry[1]);
            }
        }
        return emitter;
    }

    /**
     * 发布进度事件。若前端尚未订阅则缓冲。
     *
     * @param sessionId 构建会话ID
     * @param eventType SSE事件名（step/done/error）
     * @param data      事件数据
     */
    public void publish(String sessionId, String eventType, Map<String, Object> data) {
        if (sessionId == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(data);
            SseEmitter emitter = emitters.get(sessionId);
            if (emitter != null) {
                doSend(emitter, eventType, json);
            } else {
                buffers.computeIfAbsent(sessionId, k -> new LinkedList<>())
                       .add(new String[]{eventType, json});
            }
        } catch (Exception e) {
            log.debug("Failed to publish SSE event '{}': {}", eventType, e.getMessage());
        }
    }

    /**
     * 发布完成事件并关闭SSE连接，清理缓冲。
     *
     * @param sessionId 构建会话ID
     */
    public void complete(String sessionId) {
        publish(sessionId, "done", Map.of("message", "构建完成"));
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE complete failed: {}", e.getMessage());
            }
        }
        buffers.remove(sessionId);
    }

    /**
     * 发布错误事件并关闭连接。
     *
     * @param sessionId 构建会话ID
     * @param message   错误信息
     */
    public void error(String sessionId, String message) {
        publish(sessionId, "error", Map.of("message", message));
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE complete failed: {}", e.getMessage());
            }
        }
        buffers.remove(sessionId);
    }

    private void doSend(SseEmitter emitter, String eventType, String json) {
        try {
            emitter.send(SseEmitter.event().name(eventType).data(json));
        } catch (IOException e) {
            log.debug("SSE send failed for event '{}': {}", eventType, e.getMessage());
            emitters.values().remove(emitter);
        }
    }
}
