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
 * select_seller_source：店铺数据源
 *
 * source_type → blockId:
 *   seller_listing → SOURCE_SELLER_LIST    (SellerListFilterRequest)
 *   seller_ranking → SOURCE_SELLER_RANKLIST (SellerRanklistRequest)
 *
 * 品类参数映射到 category_id（SellerListFilterRequest/SellerRanklistRequest 均使用该字段名）。
 * 榜单排序：seller_rank_field: 1=销量榜 2=达人带货榜。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class SelectSellerSourceTool implements McpTool {

    private static final String SELLER_LISTING = "seller_listing";
    private static final String SELLER_RANKING  = "seller_ranking";

    private final SelectSourceHelper helper;

    public SelectSellerSourceTool(SelectSourceHelper helper) {
        this.helper = helper;
    }

    @Override
    public String getToolName() {
        return "select_seller_source";
    }

    @Override
    public String getTag() {
        return "echotik";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("select_seller_source",
                "选择店铺数据源（积木链第一步）。\n"
                + "- seller_listing: 店铺大盘列表\n"
                + "- seller_ranking: 店铺榜单；需传ranking_type和ranking_period；如需历史榜单可传ranking_date\n"
                + "选完后available_fields为店铺字段集，后续可使用add_filter/add_sort/traverse_entity(→商品)等。",
                schema(props(
                        prop("source_type", "string", "店铺数据源类型",
                                List.of(SELLER_LISTING, SELLER_RANKING)),
                        prop("region", "string", "目标市场地区代码", REGIONS),
                        propOpt("category", "string", "店铺经营品类关键词"),
                        propOpt("ranking_type", "string", "榜单类型，seller_ranking时必填",
                                List.of("hot_sale", "hot_promotion")),
                        propOpt("ranking_period", "string", "榜单周期，seller_ranking时必填",
                                List.of("day", "week", "month")),
                        propOpt(RANKING_DATE_PARAM, "string", RANKING_DATE_DESC),
                        propOptArrayStr("ranking_date_list",
                                "多日期列表，与ranking_date互斥（优先级更高）。用户指定日期范围时使用，" +
                                "例如[\"2026-04-01\",\"2026-04-02\",\"2026-04-03\",\"2026-04-04\"]。" +
                                "执行引擎自动按seller_id去重合并，同一店铺保留销量最大的一条。"),
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
        if (!SELLER_LISTING.equals(sourceType) && !SELLER_RANKING.equals(sourceType)) {
            return err("select_seller_source仅支持 seller_listing / seller_ranking，传入: " + sourceType);
        }

        String[] blockInfo = FieldDictionary.SOURCE_TYPE_BLOCK_MAP.get(sourceType);
        String blockId    = blockInfo[0];
        String outputType = blockInfo[1];
        String regionUp   = region.toUpperCase();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("region", regionUp);

        // 品类：店铺类 Request 使用 category_id
        String hint = helper.applyCategory(config, args, regionUp, "category_id");

        if (SELLER_RANKING.equals(sourceType)) {
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
            // seller_rank_field: 1=销量榜 2=达人带货榜
            String rankingType = (String) args.get("ranking_type");
            config.put("seller_rank_field", "hot_promotion".equals(rankingType) ? 2 : 1);
        }

        String dataVolume = (String) args.getOrDefault("data_volume", "medium");
        int totalPages = FieldDictionary.dataVolumeTotalPages(dataVolume);
        config.put("total_pages", totalPages);
        config.put("page_size", 10);

        int seq = session.getSeqCounter() + 1;
        String label = (SELLER_LISTING.equals(sourceType) ? "店铺列表" : "店铺榜单") + "(" + regionUp + ")";
        Map<String, Object> block = helper.buildBlock(seq, blockId, config, label);

        return helper.applySessionAndBuild(session, block, outputType, totalPages * 10,
                "已添加店铺数据源: " + blockId + "，地区=" + regionUp,
                hint != null ? hint : "店铺数据源已选择，可继续添加筛选(add_filter)或跳转商品(traverse_entity)");
    }

    private McpObservation err(String msg) {
        return McpObservation.builder().success(false).error(msg).build();
    }
}
