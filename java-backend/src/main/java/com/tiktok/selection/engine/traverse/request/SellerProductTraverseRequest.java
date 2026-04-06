package com.tiktok.selection.engine.traverse.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "TRAVERSE_SELLER_TO_PRODUCT",
    endpoint = "seller/product/list",
    outputType = "product_list",
    description = "店铺→商品实体跳转"
)
public class SellerProductTraverseRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        "product_id", "product_name", "region", "category_id",
        "min_price", "max_price", "spu_avg_price",
        "product_rating", "review_count",
        "total_sale_7d_cnt", "total_sale_30d_cnt",
        "total_sale_gmv_7d_amt", "total_sale_gmv_30d_amt",
        "product_commission_rate", "seller_id"
    );

    @JsonAlias("maxItems")
    @McpParam(desc = "最多处理输入店铺数量（影响API调用次数）", type = "integer", defaultValue = "50")
    public Integer max_items = 50;

    @JsonAlias("pageSize")
    @McpParam(desc = "每页商品数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @JsonAlias("maxProductsPerSeller")
    @McpParam(desc = "每个店铺最多获取商品数量", type = "integer", defaultValue = "100")
    public Integer max_products_per_seller = 100;

    @JsonAlias("sortField")
    @McpParam(
        desc = "排序字段: 1=total_sale_cnt 2=total_sale_gmv_amt 3=spu_avg_price 4=total_sale_7d_cnt 5=total_sale_gmv_7d_amt",
        type = "integer",
        enumValues = {"1", "2", "3", "4", "5"}
    )
    public Integer seller_product_sort_field;

    @JsonAlias("sortType")
    @McpParam(desc = "排序顺序: 0=asc 1=desc", type = "integer", enumValues = {"0", "1"})
    public Integer sort_type;

    public static SellerProductTraverseRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, SellerProductTraverseRequest.class);
    }

    public int getEffectiveMaxItems() {
        return max_items != null ? max_items : 50;
    }

    public int getEffectivePageSize() {
        if (page_size == null || page_size <= 0) return 10;
        return Math.min(page_size, 10);
    }

    public int getEffectiveMaxProductsPerSeller() {
        return max_products_per_seller != null ? max_products_per_seller : 100;
    }
}
