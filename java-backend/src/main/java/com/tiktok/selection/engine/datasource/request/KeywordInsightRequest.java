package com.tiktok.selection.engine.datasource.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SOURCE_KEYWORD_INSIGHT",
    endpoint = "realtime/insights/keyword",
    outputType = "keyword_list",
    description = "关键词洞察"
)
public class KeywordInsightRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        "keyword", "search_volume", "search_trend", "region",
        "competition_level", "industry", "period"
    );

    @McpParam(desc = "搜索关键词，FALSE 或不填表示不执行搜索", example = "wireless earbuds")
    public String search_keyword;

    @McpParam(desc = "国家/地区编码，nothing 代表不筛选地区，示例：US",
        enumValues = {"nothing", "TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE"})
    public String region;

    @McpParam(desc = "行业ID，nothing 代表不筛选行业，可从热门趋势-行业分类映射中选择")
    public String industry;

    @McpParam(desc = "时间范围: 7=近7天 30=近30天 120=近120天",
        enumValues = {"7", "30", "120"})
    public String period;

    @McpParam(desc = "关键词类型: 1=卖点 2=痛点 3=目标用户 4=行动号召 5=其他 6=产品，nothing=不筛选",
        enumValues = {"nothing", "1", "2", "3", "4", "5", "6"})
    public String keyword_type;

    @McpParam(desc = "投放目标: 1=访问量 2=应用安装量 3=转化量 4=视频播放量 5=覆盖人数 8=潜在客户拓展 14=商品销量，nothing=不筛选",
        enumValues = {"nothing", "1", "2", "3", "4", "5", "8", "14"})
    public String objective;

    @McpParam(desc = "分页页码", type = "string", defaultValue = "1")
    public String page = "1";

    @McpParam(desc = "单页数据条数，范围1~20", type = "string", defaultValue = "20")
    public String limit = "20";

    public static KeywordInsightRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, KeywordInsightRequest.class);
    }

    public Map<String, Object> toApiParams() {
        Map<String, Object> params = new HashMap<>();
        if (search_keyword != null) params.put("search_keyword", search_keyword);
        if (region != null) params.put("region", region);
        if (industry != null) params.put("industry", industry);
        if (period != null) params.put("period", period);
        if (keyword_type != null) params.put("keyword_type", keyword_type);
        if (objective != null) params.put("objective", objective);
        if (page != null) params.put("page", page);
        if (limit != null) params.put("limit", limit);
        return params;
    }
}
