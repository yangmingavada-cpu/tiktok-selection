package com.tiktok.selection.engine.schema;

import com.tiktok.selection.engine.annotation.request.LlmCommentAnnotationRequest;
import com.tiktok.selection.engine.compute.request.CustomFormulaComputeRequest;
import com.tiktok.selection.engine.compute.request.GrowthRateComputeRequest;
import com.tiktok.selection.engine.compute.request.ProfitMarginComputeRequest;
import com.tiktok.selection.engine.control.request.PauseWaitRequest;
import com.tiktok.selection.engine.datasource.request.InfluencerListFilterRequest;
import com.tiktok.selection.engine.datasource.request.InfluencerRanklistRequest;
import com.tiktok.selection.engine.datasource.request.KeywordInsightRequest;
import com.tiktok.selection.engine.datasource.request.ProductListFilterRequest;
import com.tiktok.selection.engine.datasource.request.ProductRanklistRequest;
import com.tiktok.selection.engine.datasource.request.SellerListFilterRequest;
import com.tiktok.selection.engine.datasource.request.SellerRanklistRequest;
import com.tiktok.selection.engine.datasource.request.TrendingHashtagListRequest;
import com.tiktok.selection.engine.datasource.request.VideoListFilterRequest;
import com.tiktok.selection.engine.datasource.request.VideoRanklistRequest;
import com.tiktok.selection.engine.enrichment.request.InfluencerDetailEnrichmentRequest;
import com.tiktok.selection.engine.enrichment.request.InfluencerTrendEnrichmentRequest;
import com.tiktok.selection.engine.enrichment.request.ProductCommentEnrichmentRequest;
import com.tiktok.selection.engine.enrichment.request.ProductDetailEnrichmentRequest;
import com.tiktok.selection.engine.enrichment.request.ProductTrendEnrichmentRequest;
import com.tiktok.selection.engine.score.request.LlmSemanticScoreRequest;
import com.tiktok.selection.engine.score.request.NumericScoreRequest;
import com.tiktok.selection.engine.score.request.ScoreAggregateRequest;
import com.tiktok.selection.engine.transform.request.ConditionalFilterTransformRequest;
import com.tiktok.selection.engine.transform.request.FieldTrimTransformRequest;
import com.tiktok.selection.engine.transform.request.SortTopNTransformRequest;
import com.tiktok.selection.engine.traverse.request.HashtagVideoTraverseRequest;
import com.tiktok.selection.engine.traverse.request.InfluencerProductTraverseRequest;
import com.tiktok.selection.engine.traverse.request.SellerProductTraverseRequest;
import com.tiktok.selection.engine.traverse.request.VideoProductTraverseRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 所有可执行 Block 的 blockId → Request 类映射注册表。
 *
 * <p>统一在这里维护，避免 ModifyBlockTool / BlockSchemaController 等多处重复列表。
 * 新增 block 时只改这一处。
 *
 * @author system
 * @date 2026/04/11
 */
public final class BlockClassRegistry {

    private BlockClassRegistry() {
    }

    /**
     * blockId → Request 类（保持插入顺序，按"数据源 → 遍历 → 富化 → 过滤变换 → 计算 → 评分 → 标注 → 控制"分组）
     */
    public static final Map<String, Class<?>> BLOCKS;

    static {
        Map<String, Class<?>> m = new LinkedHashMap<>();
        // ── 数据源 ──
        m.put("SOURCE_PRODUCT_LIST", ProductListFilterRequest.class);
        m.put("SOURCE_PRODUCT_RANKLIST", ProductRanklistRequest.class);
        m.put("SOURCE_INFLUENCER_LIST", InfluencerListFilterRequest.class);
        m.put("SOURCE_INFLUENCER_RANKLIST", InfluencerRanklistRequest.class);
        m.put("SOURCE_SELLER_LIST", SellerListFilterRequest.class);
        m.put("SOURCE_SELLER_RANKLIST", SellerRanklistRequest.class);
        m.put("SOURCE_VIDEO_LIST", VideoListFilterRequest.class);
        m.put("SOURCE_VIDEO_RANKLIST", VideoRanklistRequest.class);
        m.put("SOURCE_HASHTAG_TRENDING", TrendingHashtagListRequest.class);
        m.put("SOURCE_KEYWORD_INSIGHT", KeywordInsightRequest.class);
        // ── 遍历 ──
        m.put("TRAVERSE_INFLUENCER_TO_PRODUCT", InfluencerProductTraverseRequest.class);
        m.put("TRAVERSE_SELLER_TO_PRODUCT", SellerProductTraverseRequest.class);
        m.put("TRAVERSE_VIDEO_TO_PRODUCT", VideoProductTraverseRequest.class);
        m.put("TRAVERSE_HASHTAG_TO_VIDEO", HashtagVideoTraverseRequest.class);
        // ── 富化 ──
        m.put("ENRICH_PRODUCT_DETAIL", ProductDetailEnrichmentRequest.class);
        m.put("ENRICH_PRODUCT_TREND", ProductTrendEnrichmentRequest.class);
        m.put("ENRICH_PRODUCT_COMMENT", ProductCommentEnrichmentRequest.class);
        m.put("ENRICH_INFLUENCER_DETAIL", InfluencerDetailEnrichmentRequest.class);
        m.put("ENRICH_INFLUENCER_TREND", InfluencerTrendEnrichmentRequest.class);
        // ── 过滤 / 变换 ──
        m.put("FILTER_CONDITION", ConditionalFilterTransformRequest.class);
        m.put("TRANSFORM_FIELD_TRIM", FieldTrimTransformRequest.class);
        m.put("SORT_TOPN", SortTopNTransformRequest.class);
        // ── 控制 ──
        m.put("CONTROL_PAUSE", PauseWaitRequest.class);
        // ── 计算 ──
        m.put("COMPUTE_GROWTH_RATE", GrowthRateComputeRequest.class);
        m.put("COMPUTE_PROFIT_MARGIN", ProfitMarginComputeRequest.class);
        m.put("COMPUTE_FORMULA", CustomFormulaComputeRequest.class);
        // ── 评分 ──
        m.put("SCORE_NUMERIC", NumericScoreRequest.class);
        m.put("SCORE_SEMANTIC", LlmSemanticScoreRequest.class);
        m.put("SCORE_AGGREGATE", ScoreAggregateRequest.class);
        // ── 标注 ──
        m.put("ANNOTATE_LLM_COMMENT", LlmCommentAnnotationRequest.class);
        BLOCKS = Collections.unmodifiableMap(m);
    }

    /** 返回所有 Request 类的数组，供 SchemaGenerator.describeBlocks 使用 */
    public static Class<?>[] allClasses() {
        return BLOCKS.values().toArray(new Class<?>[0]);
    }

    /** 按 blockId 获取 Request 类，找不到返回 null */
    public static Class<?> get(String blockId) {
        return BLOCKS.get(blockId);
    }
}
