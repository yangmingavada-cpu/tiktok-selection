package com.tiktok.selection.engine.enrichment.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "ENRICH_PRODUCT_COMMENT",
    endpoint = "product/comment",
    outputType = "product_list",
    description = "商品评论补充"
)
public class ProductCommentEnrichmentRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> EXTRA_FIELDS = List.of("comments");

    @JsonAlias("pageSize")
    @McpParam(desc = "每页评论数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @JsonAlias("maxComments")
    @McpParam(desc = "每个商品最多获取评论总数", type = "integer", defaultValue = "50")
    public Integer max_comments = 50;

    public static ProductCommentEnrichmentRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, ProductCommentEnrichmentRequest.class);
    }

    public int getEffectivePageSize() {
        if (page_size == null || page_size <= 0) return 10;
        return Math.min(page_size, 10);
    }

    public int getEffectiveMaxComments() {
        return max_comments != null ? max_comments : 50;
    }
}
