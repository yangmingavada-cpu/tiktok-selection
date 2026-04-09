package com.tiktok.selection.engine.datasource.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SOURCE_INFLUENCER_RANKLIST",
    endpoint = "influencer/ranklist",
    outputType = "influencer_list",
    description = "达人榜单"
)
@SuppressWarnings("java:S116")
public class InfluencerRanklistRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        "user_id", "unique_id", "nick_name", "region",
        "category", "ec_score", "sales_flag",
        // 榜单周期内增量
        "total_followers_cnt", "total_digg_cnt",
        "total_post_video_cnt", "total_live_cnt",
        "total_sale_cnt", "total_sale_gmv_amt",
        "total_product_cnt",
        // 历史总量
        "total_followers_history_cnt", "total_digg_history_cnt",
        "total_post_video_history_cnt", "total_live_history_cnt",
        "total_sale_history_cnt", "total_sale_gmv_history_amt",
        "total_product_history_cnt",
        // 品类
        "most_category_id", "most_category_l2_id", "most_category_l3_id",
        "product_category_list"
    );

    @McpParam(desc = "目标市场地区代码", required = true,
        enumValues = {"TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE"})
    public String region;

    @McpParam(desc = "榜单日期，yyyy-MM-dd格式；周榜取周一，月榜取每月1号", required = true,
        example = "2026-03-24")
    public String date;

    @McpParam(desc = "多日期列表（与date互斥，优先级更高），yyyy-MM-dd格式")
    public List<String> date_list;

    @McpParam(desc = "榜单排序类型，1=粉丝榜 2=销量榜", required = true, type = "integer",
        enumValues = {"1", "2"})
    public Integer influencer_rank_field;

    @McpParam(desc = "榜单类型，1=天榜 2=周榜 3=月榜", required = true, type = "integer",
        enumValues = {"1", "2", "3"})
    public Integer rank_type;

    @McpParam(desc = "达人分类过滤")
    public String influencer_category_name;

    @McpParam(desc = "可对任意商品1/2/3级类目ID进行筛选")
    public String product_category_id;

    @McpParam(desc = "每页数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @McpParam(desc = "拉取页数（总条数=page_size×total_pages）", type = "integer", defaultValue = "1")
    public Integer total_pages = 1;

    public static InfluencerRanklistRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, InfluencerRanklistRequest.class);
    }

    public Map<String, Object> toBaseApiParams() {
        Map<String, Object> params = new HashMap<>();
        if (region != null) params.put("region", region);
        if (date != null) params.put("date", date);
        if (influencer_rank_field != null) params.put("influencer_rank_field", influencer_rank_field);
        if (rank_type != null) params.put("rank_type", rank_type);
        if (influencer_category_name != null) params.put("influencer_category_name", influencer_category_name);
        if (product_category_id != null) params.put("product_category_id", product_category_id);
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
