package com.tiktok.selection.engine.datasource.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SOURCE_VIDEO_LIST",
    endpoint = "video/list",
    outputType = "video_list",
    description = "视频列表筛选"
)
@SuppressWarnings("java:S116")
public class VideoListFilterRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        "video_id", "title", "region",
        "influencer_id", "product_category_id", "product_id",
        "created_by_ai", "is_ad", "sales_flag",
        "total_digg_cnt", "total_views_cnt",
        "create_time", "duration"
    );

    @McpParam(desc = "目标市场地区代码", required = true,
        enumValues = {"TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE"})
    public String region;

    @McpParam(desc = "带货的商品类目ID")
    public String product_category_id;

    @McpParam(desc = "视频带货的商品ID")
    public String product_id;

    @McpParam(desc = "是否带货视频：0=非带货 1=带货", type = "integer", enumValues = {"0", "1"})
    public Integer sales_flag;

    @McpParam(desc = "是否投流视频：0=非投流 1=投流", type = "integer", enumValues = {"0", "1"})
    public Integer is_ad;

    @McpParam(desc = "是否AI视频：true/false", enumValues = {"true", "false"})
    public String created_by_ai;

    @McpParam(desc = "发布时间范围最小值（Unix时间戳秒）", type = "integer")
    public Long min_create_time;

    @McpParam(desc = "发布时间范围最大值（Unix时间戳秒）", type = "integer")
    public Long max_create_time;

    @McpParam(desc = "视频时长最小值（秒）", type = "integer")
    public Integer min_duration;

    @McpParam(desc = "视频时长最大值（秒）", type = "integer")
    public Integer max_duration;

    @McpParam(desc = "列表排序字段：1=点赞量 2=发布时间 3=播放量", type = "integer",
        enumValues = {"1", "2", "3"})
    public Integer video_sort_field;

    @McpParam(desc = "排序顺序：0=升序 1=降序", type = "integer", enumValues = {"0", "1"})
    public Integer sort_type;

    @McpParam(desc = "每页数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @McpParam(desc = "拉取页数（总条数=page_size×total_pages）", type = "integer", defaultValue = "1")
    public Integer total_pages = 1;

    public static VideoListFilterRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, VideoListFilterRequest.class);
    }

    public Map<String, Object> toBaseApiParams() {
        Map<String, Object> params = new HashMap<>();
        if (region != null) params.put("region", region);
        if (product_category_id != null) params.put("product_category_id", product_category_id);
        if (product_id != null) params.put("product_id", product_id);
        if (sales_flag != null) params.put("sales_flag", sales_flag);
        if (is_ad != null) params.put("is_ad", is_ad);
        if (created_by_ai != null) params.put("created_by_ai", created_by_ai);
        if (min_create_time != null) params.put("min_create_time", min_create_time);
        if (max_create_time != null) params.put("max_create_time", max_create_time);
        if (min_duration != null) params.put("min_duration", min_duration);
        if (max_duration != null) params.put("max_duration", max_duration);
        if (video_sort_field != null) params.put("video_sort_field", video_sort_field);
        if (sort_type != null) params.put("sort_type", sort_type);
        int ps = (page_size != null) ? Math.min(page_size, 10) : 10;
        params.put("page_size", ps);
        return params;
    }

    public int getEffectivePageSize() {
        return (page_size != null) ? Math.min(page_size, 10) : 10;
    }

    public int getEffectiveTotalPages() {
        return (total_pages != null && total_pages > 0) ? total_pages : 1;
    }
}
