package com.tiktok.selection.dto.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SSE进度事件DTO，用于向前端推送执行进度
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class SseProgressEvent {

    /** 事件类型常量 */
    public static final String TYPE_STEP_START    = "step_start";
    public static final String TYPE_STEP_COMPLETE = "step_complete";
    public static final String TYPE_STEP_FAIL     = "step_fail";
    public static final String TYPE_SESSION_COMPLETE = "session_complete";
    public static final String TYPE_SESSION_FAIL     = "session_fail";
    public static final String TYPE_SESSION_PAUSED   = "session_paused";

    /** 最多随 SSE 推送的行数，防止单条消息过大 */
    private static final int SSE_ROW_CAP = 500;

    private String type;
    private String sessionId;
    private Integer currentStep;
    private Integer totalSteps;
    private String blockId;
    private String message;
    /** 随 step_complete 推送的数据行（最多 SSE_ROW_CAP 条） */
    private List<Map<String, Object>> rows;
    /** 随 step_complete / session_paused 推送的列定义 */
    private List<Map<String, Object>> dims;
    /** 实际总行数（rows 可能被截断时用于前端显示） */
    private Integer rowCount;
    private Long timestamp;

    public static SseProgressEvent stepStart(String sessionId, Integer step,
                                             Integer total, String blockId) {
        SseProgressEvent event = new SseProgressEvent();
        event.setType(TYPE_STEP_START);
        event.setSessionId(sessionId);
        event.setCurrentStep(step);
        event.setTotalSteps(total);
        event.setBlockId(blockId);
        event.setMessage("开始执行: " + blockId);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static SseProgressEvent stepComplete(String sessionId, Integer step,
                                                Integer total, String blockId,
                                                String message,
                                                List<Map<String, Object>> rows,
                                                List<Map<String, Object>> dims) {
        SseProgressEvent event = new SseProgressEvent();
        event.setType(TYPE_STEP_COMPLETE);
        event.setSessionId(sessionId);
        event.setCurrentStep(step);
        event.setTotalSteps(total);
        event.setBlockId(blockId);
        event.setMessage(message);
        event.setTimestamp(System.currentTimeMillis());
        if (rows != null) {
            event.setRowCount(rows.size());
            event.setRows(rows.size() > SSE_ROW_CAP ? rows.subList(0, SSE_ROW_CAP) : rows);
        }
        event.setDims(dims);
        return event;
    }

    public static SseProgressEvent sessionComplete(String sessionId, Integer totalSteps) {
        SseProgressEvent event = new SseProgressEvent();
        event.setType(TYPE_SESSION_COMPLETE);
        event.setSessionId(sessionId);
        event.setCurrentStep(totalSteps);
        event.setTotalSteps(totalSteps);
        event.setMessage("会话执行完成");
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public static SseProgressEvent sessionPaused(String sessionId, Integer step,
                                                  String blockId, String message,
                                                  List<Map<String, Object>> rows,
                                                  List<Map<String, Object>> dims) {
        SseProgressEvent event = new SseProgressEvent();
        event.setType(TYPE_SESSION_PAUSED);
        event.setSessionId(sessionId);
        event.setCurrentStep(step);
        event.setBlockId(blockId);
        event.setMessage(message != null ? message : "执行已暂停，请查看数据后继续");
        event.setTimestamp(System.currentTimeMillis());
        if (rows != null) {
            event.setRowCount(rows.size());
            event.setRows(rows.size() > SSE_ROW_CAP ? rows.subList(0, SSE_ROW_CAP) : rows);
        }
        event.setDims(dims);
        return event;
    }

    public static SseProgressEvent fail(String sessionId, Integer step,
                                        String blockId, String error) {
        SseProgressEvent event = new SseProgressEvent();
        event.setType(TYPE_SESSION_FAIL);
        event.setSessionId(sessionId);
        event.setCurrentStep(step);
        event.setBlockId(blockId);
        event.setMessage(error);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }
}
