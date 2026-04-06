package com.tiktok.selection.engine.transform.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "TRANSFORM_FIELD_TRIM",
    description = "字段裁剪：仅保留指定字段，减少后续处理的数据量"
)
public class FieldTrimTransformRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @JsonAlias("keep_fields")
    @McpParam(desc = "需要保留的字段名列表，不填则保留全部字段", type = "array",
        example = "[\"product_id\",\"product_name\",\"total_sale_30d_cnt\"]")
    public List<String> keepFields;

    public static FieldTrimTransformRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, FieldTrimTransformRequest.class);
    }
}
