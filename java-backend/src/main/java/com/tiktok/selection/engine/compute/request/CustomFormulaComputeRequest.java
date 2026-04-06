package com.tiktok.selection.engine.compute.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.Map;

@McpBlock(
    blockId = "COMPUTE_FORMULA",
    description = "自定义公式计算：支持 field_a OP field_b 和 field_a OP 常量，OP 为 +/-/*///"
)
public class CustomFormulaComputeRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @McpParam(desc = "计算公式，如 'total_sale_7d_cnt * 4.3' 或 'field_a / field_b'",
        required = true, example = "total_sale_7d_cnt * 4.3")
    public String formula;

    @JsonAlias("output_field_name")
    @McpParam(desc = "输出字段名", required = true, example = "estimated_monthly_sale")
    public String outputFieldName;

    public static CustomFormulaComputeRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, CustomFormulaComputeRequest.class);
    }
}
