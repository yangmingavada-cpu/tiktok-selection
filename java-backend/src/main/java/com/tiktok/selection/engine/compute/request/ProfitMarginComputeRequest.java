package com.tiktok.selection.engine.compute.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.Map;

@McpBlock(
    blockId = "COMPUTE_PROFIT_MARGIN",
    description = "利润率计算：(price - cost) / price * 100"
)
public class ProfitMarginComputeRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @JsonAlias("price_field")
    @McpParam(desc = "价格字段名", required = true, example = "spu_avg_price")
    public String priceField;

    @JsonAlias("cost_field")
    @McpParam(desc = "成本字段名", required = true, example = "cost_price")
    public String costField;

    @JsonAlias("output_field_name")
    @McpParam(desc = "输出字段名", defaultValue = "profit_margin")
    public String outputFieldName;

    public static ProfitMarginComputeRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, ProfitMarginComputeRequest.class);
    }

    public String getEffectiveOutputField() {
        return (outputFieldName != null && !outputFieldName.isBlank())
            ? outputFieldName : "profit_margin";
    }
}
