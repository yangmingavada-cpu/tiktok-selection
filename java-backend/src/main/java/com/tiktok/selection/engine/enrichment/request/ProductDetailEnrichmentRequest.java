package com.tiktok.selection.engine.enrichment.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;

import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "ENRICH_PRODUCT_DETAIL",
    endpoint = "product/detail",
    outputType = "product_list",
    description = "商品详情补充"
)
public class ProductDetailEnrichmentRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> EXTRA_FIELDS = List.of(
        "desc_detail", "skus", "specification", "brand_name"
    );

    public static ProductDetailEnrichmentRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, ProductDetailEnrichmentRequest.class);
    }
}
