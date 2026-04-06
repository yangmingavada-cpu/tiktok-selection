package com.tiktok.selection.engine.enrichment.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;

import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "ENRICH_INFLUENCER_DETAIL",
    endpoint = "influencer/detail",
    outputType = "influencer_list",
    description = "达人详情补充"
)
public class InfluencerDetailEnrichmentRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> EXTRA_FIELDS = List.of(
        "bio", "contact_info", "audience_demographics", "top_categories"
    );

    public static InfluencerDetailEnrichmentRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, InfluencerDetailEnrichmentRequest.class);
    }
}
