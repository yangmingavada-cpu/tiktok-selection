package com.tiktok.selection.engine.datasource.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SOURCE_INFLUENCER_LIST",
    endpoint = "influencer/list",
    outputType = "influencer_list",
    description = "达人列表筛选"
)
@SuppressWarnings("java:S116")
public class InfluencerListFilterRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        "influencer_id", "nickname", "region",
        "gender", "influencer_language",
        "total_followers_cnt", "total_followers_30d_cnt",
        "total_post_video_cnt", "per_views_avg_cnt",
        "interaction_rate", "total_product_cnt",
        "total_digg_cnt", "total_views_cnt", "total_views_7d_cnt",
        "per_video_product_views_avg_cnt", "per_video_product_views_avg_7d_cnt",
        "sales_flag", "show_case_flag"
    );

    @McpParam(desc = "目标市场地区代码", required = true,
        enumValues = {"TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE"})
    public String region;

    @McpParam(desc = "达人带货的商品一级分类ID")
    public String product_category_id;

    @McpParam(desc = "达人分类名称")
    public String influencer_category_name;

    @McpParam(desc = "达人语言")
    public String influencer_language;

    @McpParam(desc = "性别，基于头像和视频内容，仅美区生效")
    public String gender;

    @McpParam(desc = "列表排序字段，1=粉丝总量 2=近30日新增粉丝 3=发布视频数 4=平均播放量 5=互动率 6=带货商品数",
        type = "integer", enumValues = {"1", "2", "3", "4", "5", "6"})
    public Integer influencer_sort_field_v2;

    @McpParam(desc = "排序顺序，0=升序 1=降序", type = "integer", enumValues = {"0", "1"})
    public Integer sort_type;

    @McpParam(desc = "粉丝量最小值", type = "integer")
    public Integer min_total_followers_cnt;

    @McpParam(desc = "粉丝量最大值", type = "integer")
    public Integer max_total_followers_cnt;

    @McpParam(desc = "总播放量最小值", type = "integer")
    public Integer min_total_views_cnt;

    @McpParam(desc = "总播放量最大值", type = "integer")
    public Integer max_total_views_cnt;

    @McpParam(desc = "近7日播放量（增量）最小值", type = "integer")
    public Integer min_total_views_7d_cnt;

    @McpParam(desc = "近7日播放量（增量）最大值", type = "integer")
    public Integer max_total_views_7d_cnt;

    @McpParam(desc = "点赞量最小值", type = "integer")
    public Integer min_total_digg_cnt;

    @McpParam(desc = "点赞量最大值", type = "integer")
    public Integer max_total_digg_cnt;

    @McpParam(desc = "互动率最小值", type = "number")
    public Double min_interaction_rate;

    @McpParam(desc = "互动率最大值", type = "number")
    public Double max_interaction_rate;

    @McpParam(desc = "平均带货视频播放量最小值", type = "integer")
    public Integer min_per_video_product_views_avg_cnt;

    @McpParam(desc = "平均带货视频播放量最大值", type = "integer")
    public Integer max_per_video_product_views_avg_cnt;

    @McpParam(desc = "平均带货视频近7日播放量（增量）最小值", type = "integer")
    public Integer min_per_video_product_views_avg_7d_cnt;

    @McpParam(desc = "平均带货视频近7日播放量（增量）最大值", type = "integer")
    public Integer max_per_video_product_views_avg_7d_cnt;

    @McpParam(desc = "是否带货：1=视频带货 2=直播带货 3=直播+视频 4=开通橱窗",
        type = "integer", enumValues = {"1", "2", "3", "4"})
    public Integer sales_flag;

    @McpParam(desc = "是否开通橱窗：1=是 0=否", type = "integer", enumValues = {"0", "1"})
    public Integer show_case_flag;

    @McpParam(desc = "每页数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @McpParam(desc = "拉取页数（总条数=page_size×total_pages）", type = "integer", defaultValue = "1")
    public Integer total_pages = 1;

    public static InfluencerListFilterRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, InfluencerListFilterRequest.class);
    }

    public Map<String, Object> toBaseApiParams() {
        Map<String, Object> params = new HashMap<>();
        if (region != null) params.put("region", region);
        if (product_category_id != null) params.put("product_category_id", product_category_id);
        if (influencer_category_name != null) params.put("influencer_category_name", influencer_category_name);
        if (influencer_language != null) params.put("influencer_language", influencer_language);
        if (gender != null) params.put("gender", gender);
        if (influencer_sort_field_v2 != null) params.put("influencer_sort_field_v2", influencer_sort_field_v2);
        if (sort_type != null) params.put("sort_type", sort_type);
        if (min_total_followers_cnt != null) params.put("min_total_followers_cnt", min_total_followers_cnt);
        if (max_total_followers_cnt != null) params.put("max_total_followers_cnt", max_total_followers_cnt);
        if (min_total_views_cnt != null) params.put("min_total_views_cnt", min_total_views_cnt);
        if (max_total_views_cnt != null) params.put("max_total_views_cnt", max_total_views_cnt);
        if (min_total_views_7d_cnt != null) params.put("min_total_views_7d_cnt", min_total_views_7d_cnt);
        if (max_total_views_7d_cnt != null) params.put("max_total_views_7d_cnt", max_total_views_7d_cnt);
        if (min_total_digg_cnt != null) params.put("min_total_digg_cnt", min_total_digg_cnt);
        if (max_total_digg_cnt != null) params.put("max_total_digg_cnt", max_total_digg_cnt);
        if (min_interaction_rate != null) params.put("min_interaction_rate", min_interaction_rate);
        if (max_interaction_rate != null) params.put("max_interaction_rate", max_interaction_rate);
        if (min_per_video_product_views_avg_cnt != null) params.put("min_per_video_product_views_avg_cnt", min_per_video_product_views_avg_cnt);
        if (max_per_video_product_views_avg_cnt != null) params.put("max_per_video_product_views_avg_cnt", max_per_video_product_views_avg_cnt);
        if (min_per_video_product_views_avg_7d_cnt != null) params.put("min_per_video_product_views_avg_7d_cnt", min_per_video_product_views_avg_7d_cnt);
        if (max_per_video_product_views_avg_7d_cnt != null) params.put("max_per_video_product_views_avg_7d_cnt", max_per_video_product_views_avg_7d_cnt);
        if (sales_flag != null) params.put("sales_flag", sales_flag);
        if (show_case_flag != null) params.put("show_case_flag", show_case_flag);
        int ps = (page_size != null) ? Math.min(page_size, 10) : 10;
        params.put("page_size", String.valueOf(ps));
        return params;
    }

    public int getEffectivePageSize() {
        return (page_size != null) ? Math.min(page_size, 10) : 10;
    }

    public int getEffectiveTotalPages() {
        return (total_pages != null && total_pages > 0) ? total_pages : 1;
    }
}
