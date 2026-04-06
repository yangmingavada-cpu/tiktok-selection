package com.tiktok.selection.engine.datasource.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SOURCE_PRODUCT_LIST",
    endpoint = "product/list",
    outputType = "product_list",
    description = "商品列表筛选"
)
@SuppressWarnings("java:S116")
public class ProductListFilterRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        "product_id", "product_name", "region",
        "category_id", "category_l2_id", "category_l3_id",
        "min_price", "max_price", "spu_avg_price",
        "product_rating", "review_count",
        "product_commission_rate",
        "total_sale_cnt", "total_sale_7d_cnt", "total_sale_30d_cnt",
        "total_sale_gmv_amt", "total_sale_gmv_7d_amt", "total_sale_gmv_30d_amt",
        "total_video_cnt", "total_views_cnt", "total_ifl_cnt",
        "sales_trend_flag", "seller_id", "first_crawl_dt"
    );

    @McpParam(desc = "目标市场地区代码", required = true,
        enumValues = {"TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE"})
    public String region;

    @JsonAlias("categoryId")
    @McpParam(desc = "一级分类ID")
    public String category_id;

    @McpParam(desc = "二级分类ID")
    public String category_l2_id;

    @McpParam(desc = "三级分类ID")
    public String category_l3_id;

    @McpParam(desc = "是否包邮：1=是", type = "integer", enumValues = {"0", "1"})
    public Integer free_shipping;

    @McpParam(desc = "是否S级店铺：1=是", type = "integer", enumValues = {"0", "1"})
    public Integer is_s_shop;

    @McpParam(desc = "商品均价最小值(USD)", type = "number")
    public Double min_spu_avg_price;

    @McpParam(desc = "商品均价最大值(USD)", type = "number")
    public Double max_spu_avg_price;

    @McpParam(desc = "商品评分最小值", type = "number")
    public Double min_product_rating;

    @McpParam(desc = "商品评分最大值", type = "number")
    public Double max_product_rating;

    @McpParam(desc = "评论数最小值", type = "integer")
    public Integer min_review_count;

    @McpParam(desc = "评论数最大值", type = "integer")
    public Integer max_review_count;

    @McpParam(desc = "佣金比例最小值(%)", type = "number")
    public Double min_product_commission_rate;

    @McpParam(desc = "佣金比例最大值(%)", type = "number")
    public Double max_product_commission_rate;

    @McpParam(desc = "总销量最小值", type = "integer")
    public Integer min_total_sale_cnt;

    @McpParam(desc = "总销量最大值", type = "integer")
    public Integer max_total_sale_cnt;

    @McpParam(desc = "近30天销量最小值", type = "integer")
    public Integer min_total_sale_30_d_cnt;

    @McpParam(desc = "近30天销量最大值", type = "integer")
    public Integer max_total_sale_30_d_cnt;

    @McpParam(desc = "总GMV最小值(USD)", type = "number")
    public Double min_total_sale_gmv_amt;

    @McpParam(desc = "总GMV最大值(USD)", type = "number")
    public Double max_total_sale_gmv_amt;

    @McpParam(desc = "近30天GMV最小值(USD)", type = "number")
    public Double min_total_sale_gmv_30_d_amt;

    @McpParam(desc = "近30天GMV最大值(USD)", type = "number")
    public Double max_total_sale_gmv_30_d_amt;

    @McpParam(desc = "关联视频数最小值", type = "integer")
    public Integer min_total_video_cnt;

    @McpParam(desc = "关联视频数最大值", type = "integer")
    public Integer max_total_video_cnt;

    @McpParam(desc = "总播放量最小值", type = "integer")
    public Integer min_total_views_cnt;

    @McpParam(desc = "总播放量最大值", type = "integer")
    public Integer max_total_views_cnt;

    @McpParam(desc = "关联达人数最小值", type = "integer")
    public Integer min_total_ifl_cnt;

    @McpParam(desc = "关联达人数最大值", type = "integer")
    public Integer max_total_ifl_cnt;

    @McpParam(desc = "首次爬取日期最小值(yyyyMMdd)", example = "20240101")
    public String min_first_crawl_dt;

    @McpParam(desc = "首次爬取日期最大值(yyyyMMdd)", example = "20241231")
    public String max_first_crawl_dt;

    @McpParam(desc = "排序字段",
        enumValues = {"total_sale_cnt", "total_sale_30_d_cnt", "total_sale_gmv_amt",
                      "spu_avg_price", "product_rating", "review_count",
                      "product_commission_rate", "total_ifl_cnt", "first_crawl_dt"})
    public String product_sort_field;

    @McpParam(desc = "排序方向：0=降序, 1=升序", type = "integer", enumValues = {"0", "1"})
    public Integer sort_type;

    @McpParam(desc = "销量标志筛选", type = "integer")
    public Integer sales_flag;

    @McpParam(desc = "销售趋势标志：1=上升, 2=下降, 3=平稳", type = "integer",
        enumValues = {"1", "2", "3"})
    public Integer sales_trend_flag;

    @JsonAlias("pageSize")
    @McpParam(desc = "每页数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @JsonAlias("totalPages")
    @McpParam(desc = "拉取页数（总条数=page_size×total_pages）", type = "integer", defaultValue = "1")
    public Integer total_pages = 1;

    public static ProductListFilterRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, ProductListFilterRequest.class);
    }

    public Map<String, Object> toBaseApiParams() {
        Map<String, Object> params = new HashMap<>();
        if (region != null) params.put("region", region);
        if (category_id != null) params.put("category_id", category_id);
        if (category_l2_id != null) params.put("category_l2_id", category_l2_id);
        if (category_l3_id != null) params.put("category_l3_id", category_l3_id);
        if (free_shipping != null) params.put("free_shipping", free_shipping);
        if (is_s_shop != null) params.put("is_s_shop", is_s_shop);
        if (min_spu_avg_price != null) params.put("min_spu_avg_price", min_spu_avg_price);
        if (max_spu_avg_price != null) params.put("max_spu_avg_price", max_spu_avg_price);
        if (min_product_rating != null) params.put("min_product_rating", min_product_rating);
        if (max_product_rating != null) params.put("max_product_rating", max_product_rating);
        if (min_review_count != null) params.put("min_review_count", min_review_count);
        if (max_review_count != null) params.put("max_review_count", max_review_count);
        if (min_product_commission_rate != null) params.put("min_product_commission_rate", min_product_commission_rate);
        if (max_product_commission_rate != null) params.put("max_product_commission_rate", max_product_commission_rate);
        if (min_total_sale_cnt != null) params.put("min_total_sale_cnt", min_total_sale_cnt);
        if (max_total_sale_cnt != null) params.put("max_total_sale_cnt", max_total_sale_cnt);
        if (min_total_sale_30_d_cnt != null) params.put("min_total_sale_30_d_cnt", min_total_sale_30_d_cnt);
        if (max_total_sale_30_d_cnt != null) params.put("max_total_sale_30_d_cnt", max_total_sale_30_d_cnt);
        if (min_total_sale_gmv_amt != null) params.put("min_total_sale_gmv_amt", min_total_sale_gmv_amt);
        if (max_total_sale_gmv_amt != null) params.put("max_total_sale_gmv_amt", max_total_sale_gmv_amt);
        if (min_total_sale_gmv_30_d_amt != null) params.put("min_total_sale_gmv_30_d_amt", min_total_sale_gmv_30_d_amt);
        if (max_total_sale_gmv_30_d_amt != null) params.put("max_total_sale_gmv_30_d_amt", max_total_sale_gmv_30_d_amt);
        if (min_total_video_cnt != null) params.put("min_total_video_cnt", min_total_video_cnt);
        if (max_total_video_cnt != null) params.put("max_total_video_cnt", max_total_video_cnt);
        if (min_total_views_cnt != null) params.put("min_total_views_cnt", min_total_views_cnt);
        if (max_total_views_cnt != null) params.put("max_total_views_cnt", max_total_views_cnt);
        if (min_total_ifl_cnt != null) params.put("min_total_ifl_cnt", min_total_ifl_cnt);
        if (max_total_ifl_cnt != null) params.put("max_total_ifl_cnt", max_total_ifl_cnt);
        if (min_first_crawl_dt != null) params.put("min_first_crawl_dt", min_first_crawl_dt);
        if (max_first_crawl_dt != null) params.put("max_first_crawl_dt", max_first_crawl_dt);
        if (product_sort_field != null) params.put("product_sort_field", product_sort_field);
        if (sort_type != null) params.put("sort_type", sort_type);
        if (sales_flag != null) params.put("sales_flag", sales_flag);
        if (sales_trend_flag != null) params.put("sales_trend_flag", sales_trend_flag);
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
