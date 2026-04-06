package com.tiktok.selection.manager;

import com.tiktok.selection.dto.response.SseProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE连接管理器，负责SSE连接的注册、事件推送与生命周期管理。
 * 支持先发布后订阅（事件缓冲），解决 createSession 立即启动执行导致的
 * SSE 订阅竞争问题（前端 EventSource GET 请求晚于 block-exec 线程发送事件）。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);

    /** SSE超时时间：5分钟 */
    private static final Long SSE_TIMEOUT = 300000L;

    /** 会话ID -> SseEmitter 映射 */
    private final ConcurrentHashMap<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    /** 会话ID -> 未被消费的缓冲事件（订阅者尚未连接时暂存） */
    private final ConcurrentHashMap<String, Queue<SseProgressEvent>> buffers = new ConcurrentHashMap<>();

    /**
     * 已调用 complete() 但订阅者尚未到来的会话 ID 集合。
     * 用于解决"执行线程先于 EventSource GET 请求完成"的竞争：
     * complete() 不再清除 buffer，而是打上标记；subscribe() 发现标记后
     * 重放事件并立即关闭 emitter，避免连接挂起直到 5 分钟超时。
     */
    private final Set<String> completedSessions = ConcurrentHashMap.newKeySet();

    /**
     * 创建SSE连接并注册到映射表。若有缓冲事件则立即重放。
     *
     * @param sessionId 会话ID
     * @return SseEmitter实例
     */
    public SseEmitter subscribe(String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            sseEmitters.remove(sessionId);
            if (log.isDebugEnabled()) {
                log.debug("SSE completed: sessionId={}", sessionId);
            }
        });
        emitter.onTimeout(() -> {
            sseEmitters.remove(sessionId);
            if (log.isDebugEnabled()) {
                log.debug("SSE timeout: sessionId={}", sessionId);
            }
        });
        emitter.onError(ex -> {
            sseEmitters.remove(sessionId);
            log.warn("SSE error: sessionId={}", sessionId, ex);
        });

        sseEmitters.put(sessionId, emitter);
        log.info("SSE subscriber registered: sessionId={}", sessionId);

        // 重放缓冲事件（处理执行线程先于 EventSource GET 请求发送事件的情况）
        Queue<SseProgressEvent> buffered = buffers.remove(sessionId);
        if (buffered != null) {
            log.debug("Replaying {} buffered SSE events for sessionId={}", buffered.size(), sessionId);
            for (SseProgressEvent event : buffered) {
                doSend(emitter, event);
            }
        }

        // 若执行线程已调用过 complete()，立即关闭 emitter，避免连接挂起至超时
        if (completedSessions.remove(sessionId)) {
            log.debug("Session already completed before subscribe, closing emitter immediately: sessionId={}", sessionId);
            sseEmitters.remove(sessionId);
            emitter.complete();
        }

        return emitter;
    }

    /**
     * 向指定会话的SSE连接发送事件。若前端尚未订阅则缓冲。
     *
     * @param sessionId 会话ID
     * @param event     进度事件
     */
    public void sendEvent(String sessionId, SseProgressEvent event) {
        SseEmitter emitter = sseEmitters.get(sessionId);
        if (emitter != null) {
            doSend(emitter, event);
        } else {
            buffers.computeIfAbsent(sessionId, k -> new LinkedList<>()).add(event);
        }
    }

    /**
     * 关闭指定会话的SSE连接，清理缓冲。
     * 若前端尚未订阅，打上标记，等 subscribe() 到来后再关闭，避免连接挂起至超时。
     */
    public void complete(String sessionId) {
        SseEmitter emitter = sseEmitters.remove(sessionId);
        if (emitter != null) {
            // 订阅者已到，正常关闭
            buffers.remove(sessionId);
            emitter.complete();
        } else {
            // 订阅者未到：保留 buffer，打上"已结束"标记，等 subscribe() 处理收尾
            completedSessions.add(sessionId);
        }
    }

    private void doSend(SseEmitter emitter, SseProgressEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.getType()).data(event));
        } catch (IOException e) {
            sseEmitters.values().remove(emitter);
            log.warn("SSE send failed, removed emitter: sessionId={}", event.getType());
        }
    }
}
