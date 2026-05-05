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
 * select_product_source：商品数据源
 *
 * source_type → blockId:
 *   product_listing → SOURCE_PRODUCT_LIST   (ProductListFilterRequest)
 *   product_ranking → SOURCE_PRODUCT_RANKLIST (ProductRanklistRequest)
 *
 * 品类参数映射到 category_id（ProductListFilterRequest/ProductRanklistRequest 均使用该字段名）。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class SelectProductSourceTool implements McpTool {

    private static final String PRODUCT_LISTING = "product_listing";
    private static final String PRODUCT_RANKING  = "product_ranking";

    private final SelectSourceHelper helper;

    public SelectProductSourceTool(SelectSourceHelper helper) {
        this.helper = helper;
    }

    @Override
    public String getToolName() {
        return "select_product_source";
    }

    @Override
    public String getTag() {
        return "echotik";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("select_product_source",
                "选择商品数据源（积木链第一步）。\n"
                + "- product_listing: 商品大盘列表，支持按品类/价格筛选，适合全面选品普查\n"
                + "- product_ranking: 商品热销榜或热推榜；需传ranking_type和ranking_period；如需历史榜单可传ranking_date\n"
                + "选完后available_fields为商品字段集，后续可使用add_computation/add_filter/add_scoring等工具。",
                schema(props(
                        prop("source_type", "string", "商品数据源类型",
                                List.of(PRODUCT_LISTING, PRODUCT_RANKING)),
                        prop("region", "string", "目标市场地区代码", REGIONS),
                        propOpt("category", "string",
                                "品类关键词（如'家居'、'美妆'、'护肤'），Server自动匹配为对应品类参数，支持一二三级品类"),
                        propOpt("price_min", "number", "最低价格(USD)"),
                        propOpt("price_max", "number", "最高价格(USD)"),
                        propOpt("min_total_sale_30d_cnt", "integer",
                                "近30天销量最小值，强烈建议设置（如200），避免拉到 0 销量商品；" +
                                "仅 product_listing 模式生效"),
                        propOpt("max_total_sale_30d_cnt", "integer",
                                "近30天销量最大值（如30000），缩小数据范围；仅 product_listing 模式生效"),
                        propOpt("sales_trend_flag", "integer",
                                "近7天销售趋势：1=上升 2=下降 3=平稳；想筛'增长中'商品时设为 1；" +
                                "仅 product_listing 模式生效"),
                        propOpt("ranking_type", "string", "榜单类型，product_ranking时必填",
                                List.of("hot_sale", "hot_promotion")),
                        propOpt("ranking_period", "string", "榜单周期，product_ranking时必填",
                                List.of("day", "week", "month")),
                        propOpt(RANKING_DATE_PARAM, "string", RANKING_DATE_DESC),
                        propOptArrayStr("ranking_date_list",
                                "多日期列表，与ranking_date互斥（优先级更高）。用户指定日期范围时使用，" +
                                "例如[\"2026-04-01\",\"2026-04-02\",\"2026-04-03\",\"2026-04-04\"]。" +
                                "执行引擎自动按product_id去重合并，同一商品保留销量最大的一条。"),
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
        String region = (String) args.get("region");

        if (region == null) {
            return err("region为必填参数");
        }
        if (!PRODUCT_LISTING.equals(sourceType) && !PRODUCT_RANKING.equals(sourceType)) {
            return err("select_product_source仅支持 product_listing / product_ranking，传入: " + sourceType);
        }

        String[] blockInfo = FieldDictionary.SOURCE_TYPE_BLOCK_MAP.get(sourceType);
        String blockId    = blockInfo[0];
        String outputType = blockInfo[1];
        String regionUp   = region.toUpperCase();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("region", regionUp);

        // 品类：product 系列使用 category_id
        String hint = helper.applyCategory(config, args, regionUp, "category_id");

        if (PRODUCT_LISTING.equals(sourceType)) {
            // 价格范围 → min_spu_avg_price / max_spu_avg_price
            Object priceMin = args.get("price_min");
            Object priceMax = args.get("price_max");
            if (priceMin != null) config.put("min_spu_avg_price", priceMin);
            if (priceMax != null) config.put("max_spu_avg_price", priceMax);

            // 30天销量范围 + 销售趋势（避免拉到 0 销量商品）
            Object min30 = args.get("min_total_sale_30d_cnt");
            Object max30 = args.get("max_total_sale_30d_cnt");
            Object trend = args.get("sales_trend_flag");
            if (min30 != null) config.put("min_total_sale_30d_cnt", min30);
            if (max30 != null) config.put("max_total_sale_30d_cnt", max30);
            if (trend != null) config.put("sales_trend_flag", trend);
        } else {
            // 榜单参数
            Object rawDateList = args.get("ranking_date_list");
            if (rawDateList instanceof List<?> l && !l.isEmpty()) {
                // 多日期模式
                List<String> dateList = l.stream()
                        .filter(String.class::isInstance).map(String.class::cast).toList();
                config.put("date_list", dateList);
                // rank_type 默认按日榜（多日期场景通常是日榜）
                String rankPeriod = (String) args.get("ranking_period");
                int rankType = "week".equals(rankPeriod) ? 2 : "month".equals(rankPeriod) ? 3 : 1;
                config.put("rank_type", rankType);
            } else {
                // 单日期模式（原有逻辑）
                SelectSourceHelper.RankParams rp = helper.computeRankParams(
                        (String) args.get("ranking_period"), (String) args.get("ranking_date"));
                config.put("rank_type", rp.rankType());
                config.put("date", rp.date());
            }
            // product_rank_field: 1=销量榜 2=达人带货榜
            String rankingType = (String) args.get("ranking_type");
            config.put("product_rank_field", "hot_promotion".equals(rankingType) ? 2 : 1);
        }

        String dataVolume = (String) args.getOrDefault("data_volume", "medium");
        int totalPages = FieldDictionary.dataVolumeTotalPages(dataVolume);
        config.put("total_pages", totalPages);
        config.put("page_size", 10);

        int seq = session.getSeqCounter() + 1;
        String label = (PRODUCT_LISTING.equals(sourceType) ? "商品列表" : "商品榜单") + "(" + regionUp + ")";
        Map<String, Object> block = helper.buildBlock(seq, blockId, config, label);

        return helper.applySessionAndBuild(session, block, outputType, totalPages * 10,
                "已添加商品数据源: " + blockId + "，地区=" + regionUp,
                hint != null ? hint : "商品数据源已选择，可继续添加筛选(add_filter)、计算字段(add_computation)或评分(add_scoring)");
    }

    private McpObservation err(String msg) {
        return McpObservation.builder().success(false).error(msg).build();
    }
}
