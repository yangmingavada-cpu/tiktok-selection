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
 * select_video_source：视频数据源
 *
 * source_type → blockId:
 *   video_listing → SOURCE_VIDEO_LIST    (VideoListFilterRequest)
 *   video_ranking → SOURCE_VIDEO_RANKLIST (VideoRanklistRequest)
 *
 * 品类参数映射到 product_category_id（视频类 Request 使用该字段名，非 category_id）。
 * 榜单排序：video_rank_field: 1=播放量榜 2=带货销量榜。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class SelectVideoSourceTool implements McpTool {

    private static final String VIDEO_LISTING = "video_listing";
    private static final String VIDEO_RANKING  = "video_ranking";

    private final SelectSourceHelper helper;

    public SelectVideoSourceTool(SelectSourceHelper helper) {
        this.helper = helper;
    }

    @Override
    public String getToolName() {
        return "select_video_source";
    }

    @Override
    public String getTag() {
        return "echotik";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("select_video_source",
                "选择视频数据源（积木链第一步）。\n"
                + "- video_listing: 视频大盘列表\n"
                + "- video_ranking: 视频热播榜；需传ranking_type和ranking_period；如需历史榜单可传ranking_date\n"
                + "选完后available_fields为视频字段集，后续可使用add_filter/add_sort/traverse_entity(→商品)等。",
                schema(props(
                        prop("source_type", "string", "视频数据源类型",
                                List.of(VIDEO_LISTING, VIDEO_RANKING)),
                        prop("region", "string", "目标市场地区代码", REGIONS),
                        propOpt("ranking_type", "string", "榜单类型，video_ranking时必填",
                                List.of("hot_play", "hot_sale")),
                        propOpt("ranking_period", "string", "榜单周期，video_ranking时必填",
                                List.of("day", "week", "month")),
                        propOpt(RANKING_DATE_PARAM, "string", RANKING_DATE_DESC),
                        propOptArrayStr("ranking_date_list",
                                "多日期列表，与ranking_date互斥（优先级更高）。用户指定日期范围时使用，" +
                                "例如[\"2026-04-01\",\"2026-04-02\",\"2026-04-03\",\"2026-04-04\"]。" +
                                "执行引擎自动按video_id去重合并，同一视频保留销量最大的一条。"),
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
        if (!VIDEO_LISTING.equals(sourceType) && !VIDEO_RANKING.equals(sourceType)) {
            return err("select_video_source仅支持 video_listing / video_ranking，传入: " + sourceType);
        }

        String[] blockInfo = FieldDictionary.SOURCE_TYPE_BLOCK_MAP.get(sourceType);
        String blockId    = blockInfo[0];
        String outputType = blockInfo[1];
        String regionUp   = region.toUpperCase();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("region", regionUp);

        // 品类：视频类 Request 使用 product_category_id（带货商品品类）
        String hint = helper.applyCategory(config, args, regionUp, "product_category_id");

        if (VIDEO_RANKING.equals(sourceType)) {
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
            // video_rank_field: 1=播放量榜 2=带货销量榜
            String rankingType = (String) args.get("ranking_type");
            config.put("video_rank_field", "hot_play".equals(rankingType) ? 1 : 2);
        }

        String dataVolume = (String) args.getOrDefault("data_volume", "medium");
        int totalPages = FieldDictionary.dataVolumeTotalPages(dataVolume);
        config.put("total_pages", totalPages);
        config.put("page_size", 10);

        int seq = session.getSeqCounter() + 1;
        String label = (VIDEO_LISTING.equals(sourceType) ? "视频列表" : "视频榜单") + "(" + regionUp + ")";
        Map<String, Object> block = helper.buildBlock(seq, blockId, config, label);

        return helper.applySessionAndBuild(session, block, outputType, totalPages * 10,
                "已添加视频数据源: " + blockId + "，地区=" + regionUp,
                hint != null ? hint : "视频数据源已选择，可继续添加筛选(add_filter)或跳转商品(traverse_entity)");
    }

    private McpObservation err(String msg) {
        return McpObservation.builder().success(false).error(msg).build();
    }
}
