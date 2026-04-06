package com.tiktok.selection.engine.datasource.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SOURCE_HASHTAG_TRENDING",
    endpoint = "realtime/trending/popular/hashtag/list",
    outputType = "hashtag_list",
    description = "热门话题Hashtag"
)
public class TrendingHashtagListRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        "hashtag_id", "hashtag_name", "region",
        "video_count", "total_views", "avg_views_per_video"
    );

    @McpParam(desc = "国家/地区编码，默认 all_regions 代表全部地区，示例：US",
        enumValues = {"all_regions", "TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE"})
    public String region;

    @McpParam(desc = "一级行业ID，默认 nothing 代表不筛选行业，可从行业分类映射中获取")
    public String industry_id;

    @McpParam(desc = "时间范围: 7=近7天 30=近30天 120=近120天",
        enumValues = {"7", "30", "120"})
    public String period;

    @McpParam(desc = "分页页码，默认1", type = "string", defaultValue = "1")
    public String page = "1";

    @McpParam(desc = "单页数据条数，范围1~20", type = "string", defaultValue = "20")
    public String limit = "20";

    @McpParam(desc = "是否仅筛选首次进入前100的热点: true=仅首次进榜 false=不筛选",
        enumValues = {"true", "false"})
    public String new_to_top_100;

    public static TrendingHashtagListRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, TrendingHashtagListRequest.class);
    }

    public Map<String, Object> toApiParams() {
        Map<String, Object> params = new HashMap<>();
        if (region != null) params.put("region", region);
        if (industry_id != null) params.put("industry_id", industry_id);
        if (period != null) params.put("period", period);
        if (page != null) params.put("page", page);
        if (limit != null) params.put("limit", limit);
        if (new_to_top_100 != null) params.put("new_to_top_100", new_to_top_100);
        return params;
    }
}
