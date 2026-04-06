package com.tiktok.selection.engine.score.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.Map;

@McpBlock(
    blockId = "SCORE_SEMANTIC",
    description = "LLM语义评分：调用LLM对每批数据进行语义打分（消耗Token）"
)
public class LlmSemanticScoreRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @JsonAlias("semantic_prompt")
    @McpParam(desc = "评分标准描述，告诉LLM如何打分", required = true,
        example = "评估商品的创新性和差异化程度，满分100分")
    public String semanticPrompt;

    @JsonAlias("output_field")
    @McpParam(desc = "评分结果字段名", defaultValue = "semantic_score")
    public String outputField;

    @JsonAlias("max_score")
    @McpParam(desc = "满分值", type = "integer", defaultValue = "100")
    public Integer maxScore = 100;

    public static LlmSemanticScoreRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, LlmSemanticScoreRequest.class);
    }
}
