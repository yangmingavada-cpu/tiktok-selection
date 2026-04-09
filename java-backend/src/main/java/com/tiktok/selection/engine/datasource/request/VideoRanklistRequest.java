package com.tiktok.selection.engine.datasource.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SOURCE_VIDEO_RANKLIST",
    endpoint = "video/ranklist",
    outputType = "video_list",
    description = "视频榜单"
)
@SuppressWarnings("java:S116")
public class VideoRanklistRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        // 基础
        "video_id", "unique_id", "user_id", "nick_name", "region",
        "video_desc", "create_time", "duration",
        "created_by_ai", "sales_flag",

        // 互动（周期增量）
        "total_views_cnt", "total_digg_cnt",
        "total_comments_cnt", "total_favorites_cnt", "total_shares_cnt",

        // 带货（周期增量）
        "total_video_sale_cnt", "total_video_sale_gmv_amt",

        // 历史总量
        "total_views_history_cnt", "total_digg_history_cnt",
        "total_comments_history_cnt", "total_favorites_history_cnt",
        "total_shares_history_cnt",
        "total_video_sale_history_cnt", "total_video_sale_gmv_history_amt"
    );

    @McpParam(desc = "目标市场地区代码", required = true,
        enumValues = {"TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE"})
    public String region;

    @McpParam(desc = "榜单日期，yyyy-MM-dd格式；周榜取周一，月榜取每月1号", required = true,
        example = "2026-03-24")
    public String date;

    @McpParam(desc = "多日期列表（与date互斥，优先级更高），yyyy-MM-dd格式")
    public List<String> date_list;

    @McpParam(desc = "榜单排序字段，1=播放量榜 2=带货销量榜", required = true, type = "integer",
        enumValues = {"1", "2"})
    public Integer video_rank_field;

    @McpParam(desc = "榜单类型，1=天榜 2=周榜 3=月榜", required = true, type = "integer",
        enumValues = {"1", "2", "3"})
    public Integer rank_type;

    @McpParam(desc = "商品一级类目ID，用于带货视频榜单筛选")
    public String product_category_id;

    @McpParam(desc = "是否AI视频：true/false", enumValues = {"true", "false"})
    public String created_by_ai;

    @McpParam(desc = "每页数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @McpParam(desc = "拉取页数（总条数=page_size×total_pages）", type = "integer", defaultValue = "1")
    public Integer total_pages = 1;

    public static VideoRanklistRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, VideoRanklistRequest.class);
    }

    public Map<String, Object> toBaseApiParams() {
        Map<String, Object> params = new HashMap<>();
        if (region != null) params.put("region", region);
        if (date != null) params.put("date", date);
        if (video_rank_field != null) params.put("video_rank_field", video_rank_field);
        if (rank_type != null) params.put("rank_type", rank_type);
        if (product_category_id != null) params.put("product_category_id", product_category_id);
        if (created_by_ai != null) params.put("created_by_ai", created_by_ai);
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
