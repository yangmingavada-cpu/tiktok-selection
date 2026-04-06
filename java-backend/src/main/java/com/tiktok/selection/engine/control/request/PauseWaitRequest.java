package com.tiktok.selection.engine.control.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.Map;

@McpBlock(
    blockId = "CONTROL_PAUSE",
    description = "暂停等待：执行到此处时暂停，等待用户确认后继续"
)
public class PauseWaitRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @JsonAlias("pauseMessage")
    @McpParam(desc = "暂停时展示给用户的提示文字", example = "请确认筛选结果后点击继续")
    public String pause_message;

    public static PauseWaitRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, PauseWaitRequest.class);
    }
}
