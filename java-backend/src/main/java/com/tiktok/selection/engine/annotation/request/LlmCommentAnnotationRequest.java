package com.tiktok.selection.engine.annotation.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.Map;

@McpBlock(
    blockId = "ANNOTATE_LLM_COMMENT",
    description = "AI评语生成：为每条数据生成LLM评语（消耗Token）"
)
public class LlmCommentAnnotationRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @McpParam(desc = "评语语言", required = true, enumValues = {"zh", "en"})
    public String language;

    @JsonAlias("max_chars")
    @McpParam(desc = "每条评语最大字符数", type = "integer", defaultValue = "100")
    public Integer maxChars = 100;

    public static LlmCommentAnnotationRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, LlmCommentAnnotationRequest.class);
    }
}
