package com.tiktok.selection.engine.traverse.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.util.List;
import java.util.Map;

@McpBlock(
    blockId = "TRAVERSE_HASHTAG_TO_VIDEO",
    endpoint = "realtime/hashtag/video/list",
    outputType = "video_list",
    description = "话题→视频实体跳转"
)
public class HashtagVideoTraverseRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final List<String> OUTPUT_FIELDS = List.of(
        "video_id", "title", "author_id", "author_name", "region",
        "play_count", "like_count", "comment_count", "share_count",
        "publish_time", "duration_sec",
        "total_sale_cnt", "total_sale_gmv_amt"
    );

    @McpParam(desc = "地区代码，如 US、GB、ID（必填）", type = "string", required = true)
    public String region;

    @McpParam(desc = "每次请求返回的视频数量", type = "string", defaultValue = "20")
    public String count = "20";

    @JsonAlias("maxVideos")
    @McpParam(desc = "每个话题最多获取视频数量", type = "integer", defaultValue = "200")
    public Integer max_videos = 200;

    @JsonAlias("maxItems")
    @McpParam(desc = "最多处理输入话题数量", type = "integer", defaultValue = "50")
    public Integer max_items = 50;

    public static HashtagVideoTraverseRequest from(Map<String, Object> config) {
        return MAPPER.convertValue(config, HashtagVideoTraverseRequest.class);
    }

    public int getEffectiveMaxVideos() {
        return max_videos != null ? max_videos : 200;
    }

    public int getEffectiveMaxItems() {
        return max_items != null ? max_items : 50;
    }
}
