package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.FieldDictionary;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * add_sort 工具：排序+取TopN（映射为ST01块）
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class AddSortTool implements McpTool {

    @Override
    public String getToolName() {
        return "add_sort";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("add_sort",
                "按指定字段排序并截取TopN条数据。通常在完成筛选和评分后调用以缩小结果集。\n"
                + "若已有评分维度，推荐按total_score降序排序以取综合评分最高的商品。\n"
                + "调用后estimated_rows变为top_n（最终结果集大小）。\n"
                + "deduplicate=true可去除重复商品；category_disperse=true可确保结果中品类多样化（避免某一品类占据全部名额）。",
                schema(props(
                        propDynEnum("sort_by",
                                "排序字段，必须在当前available_fields中（包括评分字段如score_xxx、total_score）",
                                session.getAvailableFields(),
                                FieldDictionary.getFieldDescForType(session.getCurrentOutputType())),
                        prop("order", "string",
                                "排序方向：desc=降序（值越大越靠前），asc=升序",
                                List.of("desc", "asc")),
                        propReq("top_n", "integer", "取前N条，范围10-500，超出范围自动裁剪"),
                        propOpt("deduplicate", "boolean",
                                "是否去重，默认true。去除同一商品/达人的重复出现"),
                        propOpt("category_disperse", "boolean",
                                "是否品类分散，默认false。true时确保多品类均匀分布，避免单一品类垄断榜单")
                ), List.of("sort_by", "order", "top_n")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isHasDataSource();
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String sortBy = (String) args.get("sort_by");
        String order = (String) args.getOrDefault("order", "desc");
        Object topNObj = args.get("top_n");

        if (sortBy == null || topNObj == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("sort_by和top_n为必填参数")
                    .build();
        }

        if (!session.getAvailableFields().contains(sortBy)) {
            return McpObservation.builder()
                    .success(false)
                    .error("排序字段'" + sortBy + "'不在当前可用字段中。可用字段: " + session.getAvailableFields())
                    .build();
        }

        int topN = topNObj instanceof Number n ? n.intValue() : 50;
        topN = Math.max(10, Math.min(500, topN));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sort_by", sortBy);
        config.put("order", order);
        config.put("top_n", topN);
        config.put("deduplicate", args.getOrDefault("deduplicate", true));
        config.put("category_disperse", args.getOrDefault("category_disperse", false));

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, "SORT_TOPN", config,
                "排序取Top" + topN + ": " + sortBy + " " + order));
        session.setSeqCounter(seq);
        session.setEstimatedRowCount(Math.min(topN, session.getEstimatedRowCount()));

        return McpObservation.builder()
                .success(true)
                .message("已添加排序: " + sortBy + " " + order + "，取前" + topN + "条")
                .chainLength(session.getBlocks().size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(topN)
                .scoreFields(session.getScoreFields())
                .hint("已取Top" + topN + "。可添加AI评语(add_comment)或直接finalize_chain完成")
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
