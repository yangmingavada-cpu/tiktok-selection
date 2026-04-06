package com.tiktok.selection.engine.score.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.Map;

@McpBlock(
    blockId = "SCORE_NUMERIC",
    description = "数值型评分：对指定字段进行数值映射评分（零LLM成本）"
)
public class NumericScoreRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @JsonAlias("source_field")
    @McpParam(desc = "评分依据字段名", required = true, example = "total_sale_30d_cnt")
    public String sourceField;

    @JsonAlias("output_field")
    @McpParam(desc = "评分结果字段名", defaultValue = "score")
    public String outputField;

    @McpParam(desc = "评分算法",
        enumValues = {"linear_map", "tier_map", "inverse_map"}, defaultValue = "linear_map")
    public String algorithm = "linear_map";

    @JsonAlias("max_score")
    @McpParam(desc = "满分值", type = "integer", defaultValue = "100")
    public Integer maxScore = 100;

    public static NumericScoreRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, NumericScoreRequest.class);
    }
}
