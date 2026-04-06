package com.tiktok.selection.engine.transform.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.Map;

@McpBlock(
    blockId = "SORT_TOPN",
    description = "排序截取：按字段排序并取Top-N条"
)
public class SortTopNTransformRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @JsonAlias("sortBy")
    @McpParam(desc = "排序字段名", required = true, example = "total_sale_30d_cnt")
    public String sort_by;

    @McpParam(desc = "排序方向", enumValues = {"desc", "asc"}, defaultValue = "desc")
    public String order = "desc";

    @JsonAlias("topN")
    @McpParam(desc = "取前N条，范围10-500", type = "integer", defaultValue = "100")
    public Integer top_n = 100;

    @McpParam(desc = "是否去重", type = "boolean", defaultValue = "true")
    public Boolean deduplicate = true;

    @JsonAlias("categoryDisperse")
    @McpParam(desc = "是否品类分散", type = "boolean", defaultValue = "false")
    public Boolean category_disperse = false;

    public static SortTopNTransformRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, SortTopNTransformRequest.class);
    }

    public String getEffectiveOrder() {
        return order != null ? order : "desc";
    }

    public int getEffectiveTopN() {
        return top_n != null ? top_n : 100;
    }
}
