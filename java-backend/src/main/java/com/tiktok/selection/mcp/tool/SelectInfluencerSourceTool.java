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
 * select_influencer_source：达人数据源
 *
 * source_type → blockId:
 *   influencer_listing → SOURCE_INFLUENCER_LIST    (InfluencerListFilterRequest)
 *   influencer_ranking → SOURCE_INFLUENCER_RANKLIST (InfluencerRanklistRequest)
 *
 * 品类参数映射到 product_category_id（达人类 Request 使用该字段名，非 category_id）。
 * 榜单排序：influencer_rank_field: 1=粉丝榜 2=销量榜。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class SelectInfluencerSourceTool implements McpTool {

    private static final String INFLUENCER_LISTING = "influencer_listing";
    private static final String INFLUENCER_RANKING  = "influencer_ranking";

    private final SelectSourceHelper helper;

    public SelectInfluencerSourceTool(SelectSourceHelper helper) {
        this.helper = helper;
    }

    @Override
    public String getToolName() {
        return "select_influencer_source";
    }

    @Override
    public String getTag() {
        return "echotik";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("select_influencer_source",
                "选择达人数据源（积木链第一步）。\n"
                + "- influencer_listing: 达人大盘列表，支持按粉丝量/互动率/带货能力筛选\n"
                + "- influencer_ranking: 达人粉丝榜或带货榜；需传ranking_type和ranking_period；如需历史榜单可传ranking_date\n"
                + "选完后available_fields为达人字段集，后续可使用add_filter/add_sort/traverse_entity等工具。",
                schema(props(
                        prop("source_type", "string", "达人数据源类型",
                                List.of(INFLUENCER_LISTING, INFLUENCER_RANKING)),
                        prop("region", "string", "目标市场地区代码", REGIONS),
                        propOpt("category", "string",
                                "商品品类关键词（如'美妆'、'家居'），Server自动匹配为商品品类ID，用于筛选带货该品类的达人"),
                        propOpt("influencer_category", "string",
                                "达人自身分类名称（如'Beauty'、'Food & Cooking'、'Life Style'），"
                                + "枚举：Beauty/Food & Cooking/Health & Wellness/Clothing & Accessories/"
                                + "Home, Furniture & Appliances/Sports, Fitness & Outdoors/Gaming/"
                                + "Music & Dance/Personal Blog/Education & Training/Pets/Animals & Nature等"),
                        propOpt("ranking_type", "string",
                                "榜单类型，influencer_ranking时必填：fans=粉丝榜，hot_sale=带货榜",
                                List.of("fans", "hot_sale")),
                        propOpt("ranking_period", "string", "榜单周期，influencer_ranking时必填",
                                List.of("day", "week", "month")),
                        propOpt(RANKING_DATE_PARAM, "string", RANKING_DATE_DESC),
                        propOptArrayStr("ranking_date_list",
                                "多日期列表，与ranking_date互斥（优先级更高）。用户指定日期范围时使用，" +
                                "例如[\"2026-04-01\",\"2026-04-02\",\"2026-04-03\",\"2026-04-04\"]。" +
                                "执行引擎自动按influencer_id去重合并，同一达人保留销量最大的一条。"),
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
        if (!INFLUENCER_LISTING.equals(sourceType) && !INFLUENCER_RANKING.equals(sourceType)) {
            return err("select_influencer_source仅支持 influencer_listing / influencer_ranking，传入: " + sourceType);
        }

        String[] blockInfo = FieldDictionary.SOURCE_TYPE_BLOCK_MAP.get(sourceType);
        String blockId    = blockInfo[0];
        String outputType = blockInfo[1];
        String regionUp   = region.toUpperCase();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("region", regionUp);

        // 商品品类：达人榜单支持任意级别，达人列表只支持一级品类
        boolean forceL1 = INFLUENCER_LISTING.equals(sourceType);
        String hint = helper.applyCategory(config, args, regionUp, "product_category_id", forceL1);

        // 达人自身分类
        String influencerCat = (String) args.get("influencer_category");
        if (influencerCat != null) {
            config.put("influencer_category_name", influencerCat);
        }

        if (INFLUENCER_RANKING.equals(sourceType)) {
            Object rawDateList = args.get("ranking_date_list");
            if (rawDateList instanceof List<?> l && !l.isEmpty()) {
                List<String> dateList = l.stream()
                        .filter(String.class::isInstance).map(String.class::cast).toList();
                config.put("date_list", dateList);
                String rankPeriod = (String) args.get("ranking_period");
                int rankType = "week".equals(rankPeriod) ? 2 : "month".equals(rankPeriod) ? 3 : 1;
                config.put("rank_type", rankType);
            } else {
                SelectSourceHelper.RankParams rp = helper.computeRankParams(
                        (String) args.get("ranking_period"), (String) args.get("ranking_date"));
                config.put("rank_type", rp.rankType());
                config.put("date", rp.date());
            }
            // influencer_rank_field: 1=粉丝榜 2=销量榜
            String rankingType = (String) args.get("ranking_type");
            config.put("influencer_rank_field", "fans".equals(rankingType) ? 1 : 2);
        }

        String dataVolume = (String) args.getOrDefault("data_volume", "medium");
        int totalPages = FieldDictionary.dataVolumeTotalPages(dataVolume);
        config.put("total_pages", totalPages);
        config.put("page_size", 10);

        int seq = session.getSeqCounter() + 1;
        String label = (INFLUENCER_LISTING.equals(sourceType) ? "达人列表" : "达人榜单") + "(" + regionUp + ")";
        Map<String, Object> block = helper.buildBlock(seq, blockId, config, label);

        return helper.applySessionAndBuild(session, block, outputType, totalPages * 10,
                "已添加达人数据源: " + blockId + "，地区=" + regionUp,
                hint != null ? hint : "达人数据源已选择，可继续添加筛选(add_filter)或跳转商品(traverse_entity)");
    }

    private McpObservation err(String msg) {
        return McpObservation.builder().success(false).error(msg).build();
    }
}
