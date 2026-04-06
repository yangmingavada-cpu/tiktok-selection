package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.FieldDictionary;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * select_trending_source：趋势/洞察数据源
 *
 * source_type → blockId:
 *   hashtag_trending → SOURCE_HASHTAG_TRENDING (TrendingHashtagListRequest)
 *   keyword_insight  → SOURCE_KEYWORD_INSIGHT  (KeywordInsightRequest)
 *
 * 注意：
 * - 这两个数据源使用 page/limit（字符串）分页，不支持 total_pages/page_size，
 *   data_volume 参数在此无效，固定返回最多20条。
 * - keyword_insight 的搜索词参数名为 search_keyword（非 keyword）。
 * - 不支持品类筛选（hashtag/keyword 与商品品类无直接映射）。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class SelectTrendingSourceTool implements McpTool {

    private static final String HASHTAG_TRENDING = "hashtag_trending";
    private static final String KEYWORD_INSIGHT  = "keyword_insight";

    private final SelectSourceHelper helper;

    public SelectTrendingSourceTool(SelectSourceHelper helper) {
        this.helper = helper;
    }

    @Override
    public String getToolName() {
        return "select_trending_source";
    }

    @Override
    public String getTag() {
        return "echotik";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("select_trending_source",
                "选择趋势/洞察数据源（积木链第一步）。\n"
                + "- hashtag_trending: 热门话题Hashtag数据，适合分析当前流行内容方向\n"
                + "- keyword_insight: 关键词洞察（搜索量/热度趋势/CTR/CVR），适合市场调研和选题\n"
                + "选完后available_fields为对应字段集，通常后接add_filter/add_sort后直接finalize_chain。",
                schema(props(
                        prop("source_type", "string", "趋势数据源类型",
                                List.of(HASHTAG_TRENDING, KEYWORD_INSIGHT)),
                        prop("region", "string", "目标市场地区代码", REGIONS),
                        propOpt("keyword", "string",
                                "搜索关键词，source_type=keyword_insight时必填；hashtag_trending时可选，用于过滤包含该词的话题"),
                        propOpt("data_volume", "string",
                                "数据量：small≈50条，medium≈500条（默认），large≈2000条",
                                List.of("small", "medium", "large"))
                ), List.of("source_type", "region")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return !session.isHasDataSource();
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String sourceType = (String) args.get("source_type");
        String region     = (String) args.get("region");

        if (region == null) {
            return err("region为必填参数");
        }
        if (!HASHTAG_TRENDING.equals(sourceType) && !KEYWORD_INSIGHT.equals(sourceType)) {
            return err("select_trending_source仅支持 hashtag_trending / keyword_insight，传入: " + sourceType);
        }

        String[] blockInfo = FieldDictionary.SOURCE_TYPE_BLOCK_MAP.get(sourceType);
        String blockId    = blockInfo[0];
        String outputType = blockInfo[1];

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("region", region.toUpperCase());

        if (HASHTAG_TRENDING.equals(sourceType)) {
            // TrendingHashtagListRequest 分页参数为字符串类型
            config.put("page", "1");
            config.put("limit", "20");
            String period = (String) args.get("period");
            if (period != null) config.put("period", period);

        } else {
            // KeywordInsightRequest: 搜索词字段名为 search_keyword（非 keyword）
            String keyword = (String) args.get("keyword");
            if (keyword == null) {
                return err("source_type=keyword_insight 时 keyword 为必填参数");
            }
            config.put("search_keyword", keyword);
            config.put("page", "1");
            config.put("limit", "20");
            String period = (String) args.get("period");
            if (period != null) config.put("period", period);
        }

        int seq = session.getSeqCounter() + 1;
        String label = (HASHTAG_TRENDING.equals(sourceType) ? "热门话题" : "关键词洞察")
                + "(" + region.toUpperCase() + ")";
        Map<String, Object> block = helper.buildBlock(seq, blockId, config, label);

        // 趋势类数据源固定约20条
        return helper.applySessionAndBuild(session, block, outputType, 20,
                "已添加趋势数据源: " + blockId + "，地区=" + region.toUpperCase(),
                "趋势数据源已选择，可继续添加筛选(add_filter)或跳转(traverse_entity)");
    }

    private McpObservation err(String msg) {
        return McpObservation.builder().success(false).error(msg).build();
    }
}
