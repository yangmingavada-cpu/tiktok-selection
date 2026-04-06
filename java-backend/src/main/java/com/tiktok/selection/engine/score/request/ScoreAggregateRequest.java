package com.tiktok.selection.engine.score.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SCORE_AGGREGATE",
    description = "评分汇总：将多维度子分按权重加权求和为total_score"
)
public class ScoreAggregateRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @McpParam(desc = "评分维度列表，每项含output_field和weight", type = "array", required = true)
    public List<Map<String, Object>> dimensions;

    @JsonAlias("output_field")
    @McpParam(desc = "汇总分字段名", defaultValue = "total_score")
    public String outputField;

    public static ScoreAggregateRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, ScoreAggregateRequest.class);
    }
}
