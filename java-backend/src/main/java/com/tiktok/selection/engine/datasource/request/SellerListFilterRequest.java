package com.tiktok.selection.engine.datasource.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SOURCE_SELLER_LIST",
    endpoint = "seller/list",
    outputType = "seller_list",
    description = "店铺列表筛选"
)
@SuppressWarnings("java:S116")
public class SellerListFilterRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        // 基础信息
        "seller_id", "seller_name", "user_id", "region",
        "category_id", "category_l2_id", "category_l3_id",
        "rating", "spu_avg_price",
        "from_flag", "sales_flag", "sales_trend_flag",
        "first_crawl_dt",

        // 商品/达人/内容数
        "total_crawl_product_cnt", "total_product_cnt",
        "total_ifl_cnt",
        "total_live_cnt", "total_video_cnt",

        // 销量
        "total_sale_cnt",
        "total_sale_1d_cnt", "total_sale_7d_cnt",
        "total_sale_30d_cnt", "total_sale_90d_cnt",

        // GMV
        "total_sale_gmv_amt",
        "total_sale_gmv_1d_amt", "total_sale_gmv_7d_amt",
        "total_sale_gmv_30d_amt", "total_sale_gmv_90d_amt"
    );

    @McpParam(desc = "目标市场地区代码", required = true,
        enumValues = {"TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE"})
    public String region;

    @McpParam(desc = "店铺一级分类ID")
    public String category_id;

    @McpParam(desc = "店铺二级分类ID")
    public String category_l2_id;

    @McpParam(desc = "店铺三级分类ID")
    public String category_l3_id;

    @McpParam(desc = "店铺类型：1=本土店铺 2=跨境店铺", type = "integer", enumValues = {"1", "2"})
    public Integer from_flag;

    @McpParam(desc = "主要带货方式：1=视频 2=直播", type = "integer", enumValues = {"1", "2"})
    public Integer sales_flag;

    @McpParam(desc = "近7日销售趋势：0=平稳 1=上升 2=下降", type = "integer", enumValues = {"0", "1", "2"})
    public Integer sales_trend_flag;

    @McpParam(desc = "列表排序字段：1=总销量 2=总GMV 3=商品均价", type = "integer",
        enumValues = {"1", "2", "3"})
    public Integer seller_sort_field;

    @McpParam(desc = "排序顺序：0=升序 1=降序", type = "integer", enumValues = {"0", "1"})
    public Integer sort_type;

    @McpParam(desc = "每页数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @McpParam(desc = "拉取页数（总条数=page_size×total_pages）", type = "integer", defaultValue = "1")
    public Integer total_pages = 1;

    public static SellerListFilterRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, SellerListFilterRequest.class);
    }

    public Map<String, Object> toBaseApiParams() {
        Map<String, Object> params = new HashMap<>();
        if (region != null) params.put("region", region);
        if (category_id != null) params.put("category_id", category_id);
        if (category_l2_id != null) params.put("category_l2_id", category_l2_id);
        if (category_l3_id != null) params.put("category_l3_id", category_l3_id);
        if (from_flag != null) params.put("from_flag", from_flag);
        if (sales_flag != null) params.put("sales_flag", sales_flag);
        if (sales_trend_flag != null) params.put("sales_trend_flag", sales_trend_flag);
        if (seller_sort_field != null) params.put("seller_sort_field", seller_sort_field);
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
