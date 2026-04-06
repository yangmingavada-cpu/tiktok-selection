package com.tiktok.selection.engine.enrichment.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "ENRICH_PRODUCT_TREND",
    endpoint = "product/trend",
    outputType = "product_list",
    description = "商品历史趋势补充"
)
public class ProductTrendEnrichmentRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> EXTRA_FIELDS = List.of("trend_data");

    @JsonAlias("trendDays")
    @McpParam(desc = "趋势天数，当未指定 start_date 时，自动计算为今日往前 N 天",
        type = "integer", defaultValue = "30")
    public Integer trend_days = 30;

    @JsonAlias("startDate")
    @McpParam(desc = "开始日期 yyyy-MM-dd，指定后优先于 trend_days")
    public String start_date;

    @JsonAlias("endDate")
    @McpParam(desc = "结束日期 yyyy-MM-dd，默认为今日")
    public String end_date;

    @JsonAlias("pageSize")
    @McpParam(desc = "每页趋势记录数，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @JsonAlias("maxRecords")
    @McpParam(desc = "每个商品最多获取趋势记录总数", type = "integer", defaultValue = "100")
    public Integer max_records = 100;

    public static ProductTrendEnrichmentRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, ProductTrendEnrichmentRequest.class);
    }

    public int getEffectivePageSize() {
        if (page_size == null || page_size <= 0) return 10;
        return Math.min(page_size, 10);
    }

    public int getEffectiveTrendDays() {
        return trend_days != null ? trend_days : 30;
    }

    public int getEffectiveMaxRecords() {
        return max_records != null ? max_records : 100;
    }
}
