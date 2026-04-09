package com.tiktok.selection.mcp;

import com.tiktok.selection.engine.datasource.request.*;
import com.tiktok.selection.engine.traverse.request.*;

import java.util.*;

/**
 * 各实体类型的字段字典（与 EchoTik API 真实字段保持一致）
 *
 * @author system
 * @date 2026/03/24
 */
public final class FieldDictionary {

    private FieldDictionary() {}

    // ==================== Traverse blockId 常量（避免字符串重复）====================

    static final String BID_TRAVERSE_INFLUENCER_TO_PRODUCT = "TRAVERSE_INFLUENCER_TO_PRODUCT";
    static final String BID_TRAVERSE_SELLER_TO_PRODUCT     = "TRAVERSE_SELLER_TO_PRODUCT";
    static final String BID_TRAVERSE_VIDEO_TO_PRODUCT      = "TRAVERSE_VIDEO_TO_PRODUCT";
    static final String BID_TRAVERSE_HASHTAG_TO_VIDEO      = "TRAVERSE_HASHTAG_TO_VIDEO";

    // ==================== blockId → 真实响应字段映射 ====================
    // 直接引用各 *Request.OUTPUT_FIELDS，确保与 API 实际返回字段严格一致。
    // 链条构建时 availableFields 从此处取，而非下方的通用静态列表。

    public static final Map<String, List<String>> BLOCK_FIELD_MAP;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("SOURCE_PRODUCT_LIST",                ProductListFilterRequest.OUTPUT_FIELDS);
        m.put("SOURCE_PRODUCT_RANKLIST",            ProductRanklistRequest.OUTPUT_FIELDS);
        m.put("SOURCE_INFLUENCER_LIST",             InfluencerListFilterRequest.OUTPUT_FIELDS);
        m.put("SOURCE_INFLUENCER_RANKLIST",         InfluencerRanklistRequest.OUTPUT_FIELDS);
        m.put("SOURCE_SELLER_LIST",                 SellerListFilterRequest.OUTPUT_FIELDS);
        m.put("SOURCE_SELLER_RANKLIST",             SellerRanklistRequest.OUTPUT_FIELDS);
        m.put("SOURCE_VIDEO_LIST",                  VideoListFilterRequest.OUTPUT_FIELDS);
        m.put("SOURCE_VIDEO_RANKLIST",              VideoRanklistRequest.OUTPUT_FIELDS);
        m.put("SOURCE_HASHTAG_TRENDING",            TrendingHashtagListRequest.OUTPUT_FIELDS);
        m.put("SOURCE_KEYWORD_INSIGHT",             KeywordInsightRequest.OUTPUT_FIELDS);
        m.put(BID_TRAVERSE_INFLUENCER_TO_PRODUCT,   InfluencerProductTraverseRequest.OUTPUT_FIELDS);
        m.put(BID_TRAVERSE_SELLER_TO_PRODUCT,       SellerProductTraverseRequest.OUTPUT_FIELDS);
        m.put(BID_TRAVERSE_VIDEO_TO_PRODUCT,        VideoProductTraverseRequest.OUTPUT_FIELDS);
        m.put(BID_TRAVERSE_HASHTAG_TO_VIDEO,        HashtagVideoTraverseRequest.OUTPUT_FIELDS);
        BLOCK_FIELD_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * 按 blockId 获取该 block 的真实响应字段列表。
     * 找不到时返回空列表（调用方应 fallback 到 getFieldsForType）。
     */
    public static List<String> getFieldsForBlockId(String blockId) {
        if (blockId == null) return List.of();
        return BLOCK_FIELD_MAP.getOrDefault(blockId, List.of());
    }

    // ==================== 商品字段（58个）====================

    public static final List<String> PRODUCT_LIST_FIELDS = List.of(
            // 基础信息
            "product_id", "product_name", "region",
            "category_id", "category_l2_id", "category_l3_id",
            "min_price", "max_price", "spu_avg_price", "discount",
            "free_shipping", "is_s_shop", "off_mark",
            "product_rating", "review_count",
            "product_commission_rate",
            "sales_flag", "sales_trend_flag",
            "seller_id", "cover_url", "first_crawl_dt", "last_crawl_dt",
            "total_ifl_cnt",

            // 总销量/GMV（全量）
            "total_sale_cnt",
            "total_sale_gmv_amt",

            // 销量增量（各时间窗口）
            "total_sale_1d_cnt", "total_sale_7d_cnt", "total_sale_15d_cnt",
            "total_sale_30d_cnt", "total_sale_60d_cnt", "total_sale_90d_cnt",

            // GMV增量（各时间窗口）
            "total_sale_gmv_1d_amt", "total_sale_gmv_7d_amt", "total_sale_gmv_15d_amt",
            "total_sale_gmv_30d_amt", "total_sale_gmv_60d_amt", "total_sale_gmv_90d_amt",

            // 视频带货
            "total_video_cnt",
            "total_video_7d_cnt", "total_video_30d_cnt",
            "total_video_sale_cnt",
            "total_video_sale_7d_cnt", "total_video_sale_30d_cnt",
            "total_video_sale_gmv_amt",
            "total_video_sale_gmv_7d_amt", "total_video_sale_gmv_30d_amt",

            // 直播带货
            "total_live_cnt",
            "total_live_7d_cnt", "total_live_30d_cnt",
            "total_live_sale_cnt",
            "total_live_sale_7d_cnt", "total_live_sale_30d_cnt",
            "total_live_sale_gmv_amt",
            "total_live_sale_gmv_7d_amt", "total_live_sale_gmv_30d_amt",

            // 播放量
            "total_views_cnt",
            "total_views_7d_cnt", "total_views_30d_cnt"
    );

    public static final Map<String, String> PRODUCT_FIELD_DESC = Map.ofEntries(
            // 基础
            Map.entry("product_id",                "商品ID"),
            Map.entry("product_name",              "商品名称"),
            Map.entry("region",                    "地区代码"),
            Map.entry("category_id",               "一级品类ID"),
            Map.entry("category_l2_id",            "二级品类ID"),
            Map.entry("category_l3_id",            "三级品类ID"),
            Map.entry("min_price",                 "最低SKU价格(USD)"),
            Map.entry("max_price",                 "最高SKU价格(USD)"),
            Map.entry("spu_avg_price",             "商品SKU均价(USD)"),
            Map.entry("discount",                  "折扣"),
            Map.entry("free_shipping",             "是否包邮(1=是,0=否)"),
            Map.entry("is_s_shop",                 "是否全托管店铺(1=是,0=否)"),
            Map.entry("off_mark",                  "下架标识(<2代表在售)"),
            Map.entry("product_rating",            "商品评分(0-5)"),
            Map.entry("review_count",              "评论数"),
            Map.entry("product_commission_rate",   "佣金比例(%)"),
            Map.entry("sales_flag",                "带货方式(1=视频,2=直播)"),
            Map.entry("sales_trend_flag",          "近7日销售趋势(0=平稳,1=上升,2=下降)"),
            Map.entry("seller_id",                 "店铺ID"),
            Map.entry("cover_url",                 "商品封面图URL"),
            Map.entry("first_crawl_dt",            "首次采集时间"),
            Map.entry("last_crawl_dt",             "最近采集时间"),
            Map.entry("total_ifl_cnt",             "总带货达人数"),
            // 总量
            Map.entry("total_sale_cnt",            "总销量（榜单中为周期内销量增量）"),
            Map.entry("total_sale_gmv_amt",        "总GMV(USD)（榜单中为周期内销售额增量）"),
            // 销量增量
            Map.entry("total_sale_1d_cnt",         "近1日销量增量"),
            Map.entry("total_sale_7d_cnt",         "近7日销量增量"),
            Map.entry("total_sale_15d_cnt",        "近15日销量增量"),
            Map.entry("total_sale_30d_cnt",        "近30日销量增量"),
            Map.entry("total_sale_60d_cnt",        "近60日销量增量"),
            Map.entry("total_sale_90d_cnt",        "近90日销量增量"),
            // GMV增量
            Map.entry("total_sale_gmv_1d_amt",     "近1日GMV增量(USD)"),
            Map.entry("total_sale_gmv_7d_amt",     "近7日GMV增量(USD)"),
            Map.entry("total_sale_gmv_15d_amt",    "近15日GMV增量(USD)"),
            Map.entry("total_sale_gmv_30d_amt",    "近30日GMV增量(USD)"),
            Map.entry("total_sale_gmv_60d_amt",    "近60日GMV增量(USD)"),
            Map.entry("total_sale_gmv_90d_amt",    "近90日GMV增量(USD)"),
            // 视频带货
            Map.entry("total_video_cnt",           "总关联视频数"),
            Map.entry("total_video_7d_cnt",        "近7日新增视频数"),
            Map.entry("total_video_30d_cnt",       "近30日新增视频数"),
            Map.entry("total_video_sale_cnt",      "总视频带货销量"),
            Map.entry("total_video_sale_7d_cnt",   "近7日视频带货销量增量"),
            Map.entry("total_video_sale_30d_cnt",  "近30日视频带货销量增量"),
            Map.entry("total_video_sale_gmv_amt",  "总视频带货GMV(USD)"),
            Map.entry("total_video_sale_gmv_7d_amt",  "近7日视频带货GMV增量(USD)"),
            Map.entry("total_video_sale_gmv_30d_amt", "近30日视频带货GMV增量(USD)"),
            // 直播带货
            Map.entry("total_live_cnt",            "总直播场次"),
            Map.entry("total_live_7d_cnt",         "近7日直播场次"),
            Map.entry("total_live_30d_cnt",        "近30日直播场次"),
            Map.entry("total_live_sale_cnt",       "总直播带货销量"),
            Map.entry("total_live_sale_7d_cnt",    "近7日直播带货销量增量"),
            Map.entry("total_live_sale_30d_cnt",   "近30日直播带货销量增量"),
            Map.entry("total_live_sale_gmv_amt",   "总直播带货GMV(USD)"),
            Map.entry("total_live_sale_gmv_7d_amt",   "近7日直播带货GMV增量(USD)"),
            Map.entry("total_live_sale_gmv_30d_amt",  "近30日直播带货GMV增量(USD)"),
            // 播放量
            Map.entry("total_views_cnt",           "总视频播放量"),
            Map.entry("total_views_7d_cnt",        "近7日视频播放量增量"),
            Map.entry("total_views_30d_cnt",       "近30日视频播放量增量")
    );

    // ==================== 达人字段（52个）====================

    public static final List<String> INFLUENCER_LIST_FIELDS = List.of(
            // 基础信息
            "user_id", "unique_id", "nick_name", "region",
            "category", "language", "gender",
            "ec_score", "interaction_rate",
            "sales_flag", "show_case_flag",
            "seller_id", "off_mark", "first_crawl_dt",

            // 粉丝
            "total_followers_cnt",
            "total_followers_1d_cnt", "total_followers_7d_cnt",
            "total_followers_30d_cnt", "total_followers_90d_cnt",
            "total_following_cnt",

            // 互动
            "total_digg_cnt",
            "total_digg_1d_cnt", "total_digg_7d_cnt",
            "total_digg_30d_cnt", "total_digg_90d_cnt",
            "total_views_cnt",
            "total_shares_cnt", "total_comments_cnt",

            // 内容
            "total_post_video_cnt", "total_live_cnt",

            // 带货
            "total_sale_cnt", "total_sale_gmv_amt", "total_sale_gmv_30d_amt",
            "total_video_sale_30d_cnt", "total_video_sale_gmv_30d_amt",
            "total_live_sale_gmv_30d_amt",
            "total_product_cnt", "total_product_30d_cnt",
            "total_video_product_30d_cnt",
            "avg_30d_price",
            "per_video_product_views_avg_7d_cnt",

            // 榜单特有（历史总量）
            "total_followers_history_cnt", "total_digg_history_cnt",
            "total_post_video_history_cnt", "total_live_history_cnt",
            "total_sale_history_cnt", "total_sale_gmv_history_amt",
            "total_product_history_cnt",
            "most_category_id", "most_category_l2_id", "most_category_l3_id",
            "product_category_list"
    );

    public static final Map<String, String> INFLUENCER_FIELD_DESC = Map.ofEntries(
            Map.entry("user_id",                         "达人ID"),
            Map.entry("unique_id",                       "TikTok账号名(unique_id)"),
            Map.entry("nick_name",                       "昵称"),
            Map.entry("region",                          "地区代码"),
            Map.entry("category",                        "达人分类"),
            Map.entry("language",                        "主要语言"),
            Map.entry("gender",                          "性别"),
            Map.entry("ec_score",                        "EchoTik综合评分"),
            Map.entry("interaction_rate",                "互动率(%)"),
            Map.entry("sales_flag",                      "带货方式(1=视频,2=直播,3=视频+直播,4=橱窗)"),
            Map.entry("show_case_flag",                  "是否开通橱窗(1=是,0=否)"),
            Map.entry("seller_id",                       "关联小店ID"),
            Map.entry("off_mark",                        "是否注销(0=正常,>0=可能已注销)"),
            Map.entry("first_crawl_dt",                  "首次采集时间"),
            Map.entry("total_followers_cnt",             "总粉丝数（榜单中为周期内粉丝增量）"),
            Map.entry("total_followers_1d_cnt",          "近1日粉丝增量"),
            Map.entry("total_followers_7d_cnt",          "近7日粉丝增量"),
            Map.entry("total_followers_30d_cnt",         "近30日粉丝增量"),
            Map.entry("total_followers_90d_cnt",         "近90日粉丝增量"),
            Map.entry("total_following_cnt",             "关注数"),
            Map.entry("total_digg_cnt",                  "总点赞量（榜单中为周期内点赞增量）"),
            Map.entry("total_digg_1d_cnt",               "近1日点赞增量"),
            Map.entry("total_digg_7d_cnt",               "近7日点赞增量"),
            Map.entry("total_digg_30d_cnt",              "近30日点赞增量"),
            Map.entry("total_digg_90d_cnt",              "近90日点赞增量"),
            Map.entry("total_views_cnt",                 "总播放量"),
            Map.entry("total_shares_cnt",                "总分享量"),
            Map.entry("total_comments_cnt",              "总评论量"),
            Map.entry("total_post_video_cnt",            "总发布视频数（榜单中为周期内发布量）"),
            Map.entry("total_live_cnt",                  "总直播数（榜单中为周期内直播量）"),
            Map.entry("total_sale_cnt",                  "总带货销量(预估)（榜单中为周期内销量）"),
            Map.entry("total_sale_gmv_amt",              "总带货GMV(预估,USD)（榜单中为周期内销售额）"),
            Map.entry("total_sale_gmv_30d_amt",          "近30日带货GMV(预估,USD)"),
            Map.entry("total_video_sale_30d_cnt",        "近30日视频带货销量(预估)"),
            Map.entry("total_video_sale_gmv_30d_amt",    "近30日视频带货GMV(预估,USD)"),
            Map.entry("total_live_sale_gmv_30d_amt",     "近30日直播带货GMV(预估,USD)"),
            Map.entry("total_product_cnt",               "总带货商品数（榜单中为周期内带货商品量）"),
            Map.entry("total_product_30d_cnt",           "近30日带货商品数"),
            Map.entry("total_video_product_30d_cnt",     "近30日视频带货商品数"),
            Map.entry("avg_30d_price",                   "近30日带货商品均价(USD)"),
            Map.entry("per_video_product_views_avg_7d_cnt", "近7日带货视频平均播放量"),
            // 榜单特有字段
            Map.entry("total_followers_history_cnt",     "粉丝数总量（榜单）"),
            Map.entry("total_digg_history_cnt",          "点赞总量（榜单）"),
            Map.entry("total_post_video_history_cnt",    "发布视频数总量（榜单）"),
            Map.entry("total_live_history_cnt",          "直播数总量（榜单）"),
            Map.entry("total_sale_history_cnt",          "总销量(预估,榜单)"),
            Map.entry("total_sale_gmv_history_amt",      "总销售额(预估,USD,榜单)"),
            Map.entry("total_product_history_cnt",       "带货商品数总量（榜单）"),
            Map.entry("most_category_id",                "带货最多的一级品类ID（榜单）"),
            Map.entry("most_category_l2_id",             "带货最多的二级品类ID（榜单）"),
            Map.entry("most_category_l3_id",             "带货最多的三级品类ID（榜单）"),
            Map.entry("product_category_list",           "达人所有带货类目")
    );

    // ==================== 视频字段（31个）====================

    public static final List<String> VIDEO_LIST_FIELDS = List.of(
            // 基础
            "video_id", "unique_id", "user_id", "region",
            "video_desc", "create_time", "duration",
            "is_ad", "created_by_ai", "sales_flag",

            // 互动（总量）
            "total_views_cnt",
            "total_digg_cnt",
            "total_comments_cnt",
            "total_favorites_cnt",
            "total_shares_cnt",

            // 互动（增量）
            "total_views_1d_cnt", "total_views_7d_cnt", "total_views_30d_cnt",
            "total_digg_1d_cnt", "total_digg_7d_cnt", "total_digg_30d_cnt",

            // 带货
            "total_video_sale_cnt",
            "total_video_sale_gmv_amt",

            // 榜单特有（历史总量）
            "nick_name",
            "total_views_history_cnt", "total_digg_history_cnt",
            "total_comments_history_cnt", "total_favorites_history_cnt",
            "total_shares_history_cnt",
            "total_video_sale_history_cnt", "total_video_sale_gmv_history_amt"
    );

    public static final Map<String, String> VIDEO_FIELD_DESC = Map.ofEntries(
            Map.entry("video_id",                "视频ID"),
            Map.entry("unique_id",               "发布者TikTok账号名"),
            Map.entry("user_id",                 "发布者达人ID"),
            Map.entry("region",                  "地区代码"),
            Map.entry("video_desc",              "视频描述/文案"),
            Map.entry("create_time",             "视频发布时间"),
            Map.entry("duration",                "视频时长(秒)"),
            Map.entry("is_ad",                   "是否投流视频(1=是,0=否)"),
            Map.entry("created_by_ai",           "是否AI生成视频"),
            Map.entry("sales_flag",              "是否带货视频(1=是,0=否)"),
            Map.entry("total_views_cnt",         "总播放量"),
            Map.entry("total_digg_cnt",          "总点赞量"),
            Map.entry("total_comments_cnt",      "总评论数"),
            Map.entry("total_favorites_cnt",     "总收藏量"),
            Map.entry("total_shares_cnt",        "总分享量"),
            Map.entry("total_views_1d_cnt",      "近1日播放增量"),
            Map.entry("total_views_7d_cnt",      "近7日播放增量"),
            Map.entry("total_views_30d_cnt",     "近30日播放增量"),
            Map.entry("total_digg_1d_cnt",       "近1日点赞增量"),
            Map.entry("total_digg_7d_cnt",       "近7日点赞增量"),
            Map.entry("total_digg_30d_cnt",      "近30日点赞增量"),
            Map.entry("total_video_sale_cnt",    "带货总销量(预估)（榜单中为周期内销量增量）"),
            Map.entry("total_video_sale_gmv_amt","带货总GMV(预估,USD)（榜单中为周期内销售额增量）"),
            // 榜单特有
            Map.entry("nick_name",                       "发布者昵称（榜单）"),
            Map.entry("total_views_history_cnt",         "播放量总量（榜单）"),
            Map.entry("total_digg_history_cnt",          "点赞总量（榜单）"),
            Map.entry("total_comments_history_cnt",      "评论总量（榜单）"),
            Map.entry("total_favorites_history_cnt",     "收藏总量（榜单）"),
            Map.entry("total_shares_history_cnt",        "分享总量（榜单）"),
            Map.entry("total_video_sale_history_cnt",    "带货销量总量(预估,榜单)"),
            Map.entry("total_video_sale_gmv_history_amt","带货GMV总量(预估,USD,榜单)")
    );

    // ==================== 店铺字段（28个）====================

    public static final List<String> SELLER_LIST_FIELDS = List.of(
            // 基础
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

    public static final Map<String, String> SELLER_FIELD_DESC = Map.ofEntries(
            Map.entry("seller_id",              "店铺ID"),
            Map.entry("seller_name",            "店铺名称"),
            Map.entry("user_id",                "达人UID"),
            Map.entry("region",                 "地区代码"),
            Map.entry("category_id",            "主营一级品类ID"),
            Map.entry("category_l2_id",         "主营二级品类ID"),
            Map.entry("category_l3_id",         "主营三级品类ID"),
            Map.entry("rating",                 "店铺评分"),
            Map.entry("spu_avg_price",          "店铺商品均价(USD)"),
            Map.entry("from_flag",              "店铺类型(1=本土,2=跨境)"),
            Map.entry("sales_flag",             "主要带货方式(1=视频,2=直播)"),
            Map.entry("sales_trend_flag",       "近7日销售趋势(0=平稳,1=上升,2=下降)"),
            Map.entry("first_crawl_dt",         "首次采集时间"),
            Map.entry("total_crawl_product_cnt","当前在售商品数"),
            Map.entry("total_product_cnt",      "历史在售商品数(含下架)"),
            Map.entry("total_ifl_cnt",          "关联带货达人数"),
            Map.entry("total_live_cnt",         "总直播数"),
            Map.entry("total_video_cnt",        "总关联视频数"),
            Map.entry("total_sale_cnt",         "总销量"),
            Map.entry("total_sale_1d_cnt",      "近1日销量增量"),
            Map.entry("total_sale_7d_cnt",      "近7日销量增量"),
            Map.entry("total_sale_30d_cnt",     "近30日销量增量"),
            Map.entry("total_sale_90d_cnt",     "近90日销量增量"),
            Map.entry("total_sale_gmv_amt",     "总GMV(USD)"),
            Map.entry("total_sale_gmv_1d_amt",  "近1日GMV增量(USD)"),
            Map.entry("total_sale_gmv_7d_amt",  "近7日GMV增量(USD)"),
            Map.entry("total_sale_gmv_30d_amt", "近30日GMV增量(USD)"),
            Map.entry("total_sale_gmv_90d_amt", "近90日GMV增量(USD)")
    );

    // ==================== Hashtag字段（6个）====================

    public static final List<String> HASHTAG_LIST_FIELDS = List.of(
            "hashtag_id", "hashtag_name", "region",
            "video_count", "total_views", "avg_views_per_video"
    );

    public static final Map<String, String> HASHTAG_FIELD_DESC = Map.of(
            "hashtag_id",           "话题ID",
            "hashtag_name",         "话题名称",
            "region",               "地区代码",
            "video_count",          "关联视频数",
            "total_views",          "总播放量",
            "avg_views_per_video",  "每视频平均播放量"
    );

    // ==================== 关键词字段（5个）====================

    public static final List<String> KEYWORD_LIST_FIELDS = List.of(
            "keyword", "search_volume", "search_trend", "region", "competition_level"
    );

    public static final Map<String, String> KEYWORD_FIELD_DESC = Map.of(
            "keyword",           "关键词",
            "search_volume",     "搜索量",
            "search_trend",      "搜索趋势方向",
            "region",            "地区代码",
            "competition_level", "竞争程度"
    );

    // ==================== 评论字段（使用 EchoTik API 真实字段名）====================

    public static final List<String> COMMENT_LIST_FIELDS = List.of(
            "review_id", "product_id", "display_text",
            "rating", "review_timestamp",
            "sku_id", "sku_specification"
    );

    public static final Map<String, String> COMMENT_FIELD_DESC = Map.of(
            "review_id",         "评论ID",
            "product_id",        "所属商品ID",
            "display_text",      "评论内容",
            "rating",            "评分(1-5)",
            "review_timestamp",  "评论时间戳",
            "sku_id",            "评论对应的SKU ID",
            "sku_specification", "评论对应的SKU规格"
    );

    // ==================== 字段描述统一查询 ====================

    /**
     * 获取指定实体类型的字段列表
     */
    public static List<String> getFieldsForType(String outputType) {
        return switch (outputType) {
            case "product_list"    -> PRODUCT_LIST_FIELDS;
            case "influencer_list" -> INFLUENCER_LIST_FIELDS;
            case "video_list"      -> VIDEO_LIST_FIELDS;
            case "seller_list"     -> SELLER_LIST_FIELDS;
            case "hashtag_list"    -> HASHTAG_LIST_FIELDS;
            case "keyword_list"    -> KEYWORD_LIST_FIELDS;
            case "comment_list"    -> COMMENT_LIST_FIELDS;
            default                -> List.of();
        };
    }

    /**
     * 获取指定实体类型的字段中文描述 Map
     */
    public static Map<String, String> getFieldDescForType(String outputType) {
        return switch (outputType) {
            case "product_list"    -> PRODUCT_FIELD_DESC;
            case "influencer_list" -> INFLUENCER_FIELD_DESC;
            case "video_list"      -> VIDEO_FIELD_DESC;
            case "seller_list"     -> SELLER_FIELD_DESC;
            case "hashtag_list"    -> HASHTAG_FIELD_DESC;
            case "keyword_list"    -> KEYWORD_FIELD_DESC;
            case "comment_list"    -> COMMENT_FIELD_DESC;
            default                -> Map.of();
        };
    }

    // ==================== 地区列表 ====================

    public static final List<Map<String, String>> REGIONS = List.of(
            Map.of("code", "TH", "name_zh", "泰国",       "name_en", "Thailand"),
            Map.of("code", "US", "name_zh", "美国",       "name_en", "United States"),
            Map.of("code", "UK", "name_zh", "英国",       "name_en", "United Kingdom"),
            Map.of("code", "ID", "name_zh", "印度尼西亚", "name_en", "Indonesia"),
            Map.of("code", "MY", "name_zh", "马来西亚",   "name_en", "Malaysia"),
            Map.of("code", "PH", "name_zh", "菲律宾",     "name_en", "Philippines"),
            Map.of("code", "VN", "name_zh", "越南",       "name_en", "Vietnam"),
            Map.of("code", "SG", "name_zh", "新加坡",     "name_en", "Singapore"),
            Map.of("code", "SA", "name_zh", "沙特阿拉伯", "name_en", "Saudi Arabia"),
            Map.of("code", "AE", "name_zh", "阿联酋",     "name_en", "United Arab Emirates")
    );

    // ==================== 实体跳转规则 ====================

    public static final Map<String, List<String>> TRAVERSE_OPTIONS = Map.of(
            "influencer_list", List.of("influencer_to_product"),
            "video_list",      List.of("video_to_product"),
            "seller_list",     List.of("seller_to_product"),
            "hashtag_list",    List.of("hashtag_to_video")
    );

    public static final Map<String, String[]> TRAVERSE_BLOCK_MAP = Map.of(
            "influencer_to_product", new String[]{BID_TRAVERSE_INFLUENCER_TO_PRODUCT, "product_list"},
            "video_to_product",      new String[]{BID_TRAVERSE_VIDEO_TO_PRODUCT,      "product_list"},
            "seller_to_product",     new String[]{BID_TRAVERSE_SELLER_TO_PRODUCT,     "product_list"},
            "hashtag_to_video",      new String[]{BID_TRAVERSE_HASHTAG_TO_VIDEO,      "video_list"}
    );

    // ==================== 连接规则 ====================

    public static final Map<String, Set<String>> CONNECTION_RULES;

    static {
        Map<String, Set<String>> rules = new LinkedHashMap<>();
        rules.put("product_list", new LinkedHashSet<>(Arrays.asList(
                "TRANSFORM_FIELD_TRIM", "COMPUTE_GROWTH_RATE", "COMPUTE_PROFIT_MARGIN", "COMPUTE_FORMULA", "FILTER_CONDITION",
                "SCORE_AGGREGATE", "SCORE_NUMERIC", "SCORE_SEMANTIC", "SORT_TOPN",
                "ENRICH_PRODUCT_DETAIL", "ENRICH_PRODUCT_TREND",
                "ANNOTATE_LLM_COMMENT", "OUTPUT_FINAL", "CONTROL_PAUSE")));
        rules.put("influencer_list", new LinkedHashSet<>(Arrays.asList(
                BID_TRAVERSE_INFLUENCER_TO_PRODUCT, "ENRICH_INFLUENCER_DETAIL", "ENRICH_INFLUENCER_TREND",
                "FILTER_CONDITION", "SORT_TOPN", "CONTROL_PAUSE")));
        rules.put("video_list", new LinkedHashSet<>(Arrays.asList(
                BID_TRAVERSE_VIDEO_TO_PRODUCT, "FILTER_CONDITION", "SORT_TOPN", "CONTROL_PAUSE")));
        rules.put("seller_list", new LinkedHashSet<>(Arrays.asList(
                BID_TRAVERSE_SELLER_TO_PRODUCT, "FILTER_CONDITION", "SORT_TOPN", "CONTROL_PAUSE")));
        rules.put("hashtag_list", new LinkedHashSet<>(Arrays.asList(
                BID_TRAVERSE_HASHTAG_TO_VIDEO, "FILTER_CONDITION", "CONTROL_PAUSE")));
        rules.put("comment_list", new LinkedHashSet<>(Arrays.asList(
                "ENRICH_PRODUCT_COMMENT", "CONTROL_PAUSE")));
        rules.put("scored_list",  new LinkedHashSet<>(Arrays.asList(
                "SORT_TOPN", "ANNOTATE_LLM_COMMENT", "OUTPUT_FINAL", "CONTROL_PAUSE")));
        CONNECTION_RULES = Collections.unmodifiableMap(rules);
    }

    // ==================== 数据源类型映射 ====================

    public static final Map<String, String[]> SOURCE_TYPE_BLOCK_MAP = new LinkedHashMap<>();

    static {
        SOURCE_TYPE_BLOCK_MAP.put("product_listing",    new String[]{"SOURCE_PRODUCT_LIST",         "product_list"});
        SOURCE_TYPE_BLOCK_MAP.put("product_ranking",    new String[]{"SOURCE_PRODUCT_RANKLIST",     "product_list"});
        SOURCE_TYPE_BLOCK_MAP.put("influencer_listing", new String[]{"SOURCE_INFLUENCER_LIST",      "influencer_list"});
        SOURCE_TYPE_BLOCK_MAP.put("influencer_ranking", new String[]{"SOURCE_INFLUENCER_RANKLIST",  "influencer_list"});
        SOURCE_TYPE_BLOCK_MAP.put("seller_listing",     new String[]{"SOURCE_SELLER_LIST",          "seller_list"});
        SOURCE_TYPE_BLOCK_MAP.put("seller_ranking",     new String[]{"SOURCE_SELLER_RANKLIST",      "seller_list"});
        SOURCE_TYPE_BLOCK_MAP.put("video_listing",      new String[]{"SOURCE_VIDEO_LIST",           "video_list"});
        SOURCE_TYPE_BLOCK_MAP.put("video_ranking",      new String[]{"SOURCE_VIDEO_RANKLIST",       "video_list"});
        SOURCE_TYPE_BLOCK_MAP.put("hashtag_trending",   new String[]{"SOURCE_HASHTAG_TRENDING",     "hashtag_list"});
        SOURCE_TYPE_BLOCK_MAP.put("keyword_insight",    new String[]{"SOURCE_KEYWORD_INSIGHT",      "keyword_list"});
    }

    public static int dataVolumeTotalPages(String dataVolume) {
        return switch (dataVolume) {
            case "small"  -> 5;
            case "large"  -> 200;
            default       -> 50; // medium
        };
    }
}
