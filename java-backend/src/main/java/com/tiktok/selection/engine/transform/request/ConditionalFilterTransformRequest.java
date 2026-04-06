package com.tiktok.selection.engine.transform.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.Map;

@McpBlock(
    blockId = "FILTER_CONDITION",
    description = "条件筛选：按单字段条件过滤数据行"
)
public class ConditionalFilterTransformRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @McpParam(desc = "筛选字段名", required = true, example = "product_rating")
    public String field;

    @McpParam(desc = "比较运算符", required = true,
        enumValues = {">", ">=", "<", "<=", "==", "!=", "between", "in", "contains"})
    public String operator;

    @McpParam(desc = "比较值（between时传[min,max]数组，in时传值数组，其他传单值）",
        required = true, example = "4.5")
    public Object value;

    @JsonAlias("valueTo")
    @McpParam(desc = "between运算符的上限值", example = "5.0")
    public Object value_to;

    public static ConditionalFilterTransformRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, ConditionalFilterTransformRequest.class);
    }
}
