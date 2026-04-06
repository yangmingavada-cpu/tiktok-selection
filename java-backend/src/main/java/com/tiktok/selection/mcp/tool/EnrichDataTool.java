package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * enrich_data 工具：数据补充（映射为EN系列块）
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class EnrichDataTool implements McpTool {

    private static final String TREND_DAYS_KEY = "trend_days";

    @Override
    public String getToolName() {
        return "enrich_data";
    }

    @Override
    public String getTag() {
        return "echotik";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        String outputType = session.getCurrentOutputType();
        List<String> types;
        String desc;
        switch (outputType != null ? outputType : "") {
            case "product_list" -> {
                types = List.of("product_detail", "product_trend", "comment_sentiment");
                desc = "数据补充（调用Echotik接口为当前商品列表追加字段）。\n"
                     + "各enrichment_type新增字段：\n"
                     + "- product_detail: 商品详情，新增 desc_detail/skus/specification/brand_name，适合需要商品描述做语义评分时\n"
                     + "- product_trend: 历史销售趋势，新增 trend_data/trend_peak_date/trend_direction，适合判断商品是否仍处于上升期\n"
                     + "- comment_sentiment: 评论情感分析（LLM驱动），新增 sentiment_positive/sentiment_negative/sentiment_summary，消耗额外Token";
            }
            case "influencer_list" -> {
                types = List.of("influencer_detail", "influencer_trend");
                desc = "数据补充（调用Echotik接口为当前达人列表追加字段）。\n"
                     + "各enrichment_type新增字段：\n"
                     + "- influencer_detail: 达人详情，新增 bio/contact_info/audience_demographics/top_categories\n"
                     + "- influencer_trend: 达人历史趋势，新增 trend_data/trend_peak_date/trend_direction/fans_growth_trend";
            }
            case "video_list" -> {
                types = List.of("video_trend");
                desc = "数据补充（调用Echotik接口为当前视频列表追加字段）。\n"
                     + "- video_trend: 视频近14日互动趋势，新增 trend_views/trend_likes/trend_comments";
            }
            default -> {
                types = List.of();
                desc = "数据补充";
            }
        }
        return tool("enrich_data", desc,
                schema(props(
                        prop("enrichment_type", "string", "补充类型，选择后对应字段加入available_fields", types),
                        propOpt(TREND_DAYS_KEY, "integer", "trend类型时的历史天数，最大180天，默认30天")
                ), List.of("enrichment_type")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isHasDataSource()
                && List.of("product_list", "influencer_list", "video_list")
                       .contains(session.getCurrentOutputType());
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String enrichmentType = (String) args.get("enrichment_type");
        if (enrichmentType == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("enrichment_type为必填参数")
                    .build();
        }

        String blockId;
        String label;
        List<String> newFields = new ArrayList<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enrichment_type", enrichmentType);

        switch (enrichmentType) {
            case "product_detail" -> {
                blockId = "ENRICH_PRODUCT_DETAIL";
                label = "商品详情补充";
                newFields.addAll(List.of("desc_detail", "skus", "specification", "brand_name"));
            }
            case "product_trend" -> {
                blockId = "ENRICH_PRODUCT_TREND";
                label = "商品历史趋势";
                int trendDays = args.get(TREND_DAYS_KEY) instanceof Number n ? n.intValue() : 30;
                config.put(TREND_DAYS_KEY, Math.min(trendDays, 180));
                newFields.addAll(List.of("trend_data", "trend_peak_date", "trend_direction"));
            }
            case "comment_sentiment" -> {
                blockId = "ENRICH_PRODUCT_COMMENT";
                label = "评论情感分析(LLM)";
                newFields.addAll(List.of("sentiment_positive", "sentiment_negative",
                        "sentiment_neutral", "sentiment_summary"));
            }
            case "video_trend" -> {
                blockId = "ENRICH_VIDEO_TREND";
                label = "视频14日互动趋势";
                newFields.addAll(List.of("trend_views", "trend_likes", "trend_comments"));
            }
            case "influencer_detail" -> {
                blockId = "ENRICH_INFLUENCER_DETAIL";
                label = "达人详情补充";
                newFields.addAll(List.of("bio", "contact_info",
                        "audience_demographics", "top_categories"));
            }
            case "influencer_trend" -> {
                blockId = "ENRICH_INFLUENCER_TREND";
                label = "达人历史趋势";
                int trendDays = args.get(TREND_DAYS_KEY) instanceof Number n ? n.intValue() : 30;
                config.put("trendDays", Math.min(trendDays, 180));
                newFields.addAll(List.of("trend_data", "trend_peak_date",
                        "trend_direction", "fans_growth_trend"));
            }
            default -> {
                return McpObservation.builder()
                        .success(false)
                        .error("未知enrichment_type: " + enrichmentType
                                + "。支持: product_detail, product_trend, influencer_detail,"
                                + " influencer_trend, comment_sentiment, video_trend")
                        .build();
            }
        }

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, blockId, config, label));
        session.setSeqCounter(seq);
        session.getAvailableFields().addAll(newFields);

        return McpObservation.builder()
                .success(true)
                .message("已添加数据补充: " + label + "，新增" + newFields.size() + "个字段")
                .chainLength(session.getBlocks().size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(session.getEstimatedRowCount())
                .scoreFields(session.getScoreFields())
                .hint("补充字段已加入available_fields，可用于后续筛选或评分")
                .build();
    }

    private Map<String, Object> buildBlock(int seq, String blockId,
                                            Map<String, Object> config, String label) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("seq", seq);
        block.put("blockId", blockId);
        block.put("label", label);
        block.put("config", config);
        return block;
    }
}
