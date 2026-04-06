package com.tiktok.selection.engine.compute.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.Map;

@McpBlock(
    blockId = "COMPUTE_GROWTH_RATE",
    description = "增长率计算：(A - B) / B * 100"
)
public class GrowthRateComputeRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @JsonAlias("fieldA")
    @McpParam(desc = "分子字段A（如近7日销量）", required = true, example = "total_sale_7d_cnt")
    public String field_a;

    @JsonAlias("fieldB")
    @McpParam(desc = "分母字段B（如近30日销量/4.3换算为周均）", required = true, example = "total_sale_30d_cnt")
    public String field_b;

    @JsonAlias("outputFieldName")
    @McpParam(desc = "输出字段名", defaultValue = "growth_rate")
    public String output_field_name;

    public static GrowthRateComputeRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, GrowthRateComputeRequest.class);
    }

    public String getEffectiveOutputField() {
        return (output_field_name != null && !output_field_name.isBlank())
            ? output_field_name : "growth_rate";
    }
}
