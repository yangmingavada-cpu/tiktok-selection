package com.tiktok.selection.engine.traverse.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "TRAVERSE_VIDEO_TO_PRODUCT",
    endpoint = "video/product/list",
    outputType = "product_list",
    description = "视频→商品实体跳转"
)
public class VideoProductTraverseRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // 注意：API 实际返回视频数据（带 product_id），而非商品数据
    // outputType 声明为 "product_list" 但数据结构为视频，需后续评估是否调整
    public static final List<String> OUTPUT_FIELDS = List.of(
        "video_id", "user_id", "region",
        "video_desc", "create_time", "duration",
        "product_id",
        "total_views_cnt", "total_digg_cnt",
        "total_comments_cnt", "total_favorites_cnt", "total_shares_cnt",
        "total_video_sale_cnt", "total_video_sale_gmv_amt"
    );

    @JsonAlias("pageSize")
    @McpParam(desc = "每页商品数量，最大10", type = "integer", defaultValue = "10")
    public Integer page_size = 10;

    @JsonAlias("batchSize")
    @McpParam(desc = "每次API调用传入的视频ID数量（批量查询）", type = "integer", defaultValue = "20")
    public Integer batch_size = 20;

    @JsonAlias("maxProducts")
    @McpParam(desc = "最多获取商品总数量", type = "integer", defaultValue = "200")
    public Integer max_products = 200;

    public static VideoProductTraverseRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, VideoProductTraverseRequest.class);
    }

    public int getEffectivePageSize() {
        if (page_size == null || page_size <= 0) return 10;
        return Math.min(page_size, 10);
    }

    public int getEffectiveBatchSize() {
        if (batch_size == null || batch_size <= 0) return 20;
        return batch_size;
    }

    public int getEffectiveMaxProducts() {
        return max_products != null ? max_products : 200;
    }
}
