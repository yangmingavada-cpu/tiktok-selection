package com.tiktok.selection.engine.datasource.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "SOURCE_SELLER_RANKLIST",
    endpoint = "seller/ranklist",
    outputType = "seller_list",
    description = "店铺榜单"
)
@SuppressWarnings("java:S116")
public class SellerRanklistRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        "seller_id", "seller_name", "region",
        "category_id", "category_l2_id", "category_l3_id",
        "total_sale_cnt", "total_sale_gmv_amt", "total_ifl_cnt",
        "rank_position"
    );

    @McpParam(desc = "目标市场地区代码", required = true,
        enumValues = {"TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE"})
    public String region;

    @McpParam(desc = "榜单日期，yyyy-MM-dd格式；周榜取周一，月榜取每月1号", required = true,
        example = "2026-03-24")
    public String date;

    @McpParam(desc = "多日期列表（与date互斥，优先级更高），yyyy-MM-dd格式")
    public List<String> date_list;

    @McpParam(desc = "榜单排序字段，1=销量榜 2=达人带货榜", required = true, type = "integer",
        enumValues = {"1", "2"})
    public Integer seller_rank_field;

    @McpParam(desc = "榜单类型，1=天榜 2=周榜 3=月榜", required = true, type = "integer",
        enumValues = {"1", "2", "3"})
    public Integer rank_type;

    @McpParam(desc = "一级类目ID")
    public String category_id;

    @McpParam(desc = "二级类目ID")
    public String category_l2_id;

    @McpParam(desc = "三级类目ID")
    public String category_l3_id;

    @McpParam(desc = "每页数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @McpParam(desc = "拉取页数（总条数=page_size×total_pages）", type = "integer", defaultValue = "1")
    public Integer total_pages = 1;

    public static SellerRanklistRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, SellerRanklistRequest.class);
    }

    public Map<String, Object> toBaseApiParams() {
        Map<String, Object> params = new HashMap<>();
        if (region != null) params.put("region", region);
        if (date != null) params.put("date", date);
        if (seller_rank_field != null) params.put("seller_rank_field", seller_rank_field);
        if (rank_type != null) params.put("rank_type", rank_type);
        if (category_id != null) params.put("category_id", category_id);
        if (category_l2_id != null) params.put("category_l2_id", category_l2_id);
        if (category_l3_id != null) params.put("category_l3_id", category_l3_id);
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
