package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.FieldDictionary;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * traverse_entity 工具：实体跳转（映射为TR系列块）
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class TraverseEntityTool implements McpTool {

    @Override
    public String getToolName() {
        return "traverse_entity";
    }

    @Override
    public String getTag() {
        return "echotik";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        String currentOutputType = session.getCurrentOutputType();
        List<String> traverseOptions = FieldDictionary.TRAVERSE_OPTIONS
                .getOrDefault(currentOutputType, List.of());
        return tool("traverse_entity",
                "实体跳转：将数据流从当前实体类型切换到关联实体类型。\n"
                + "调用后available_fields将完全替换为目标实体的字段集，之前的商品/达人字段不再可用。\n"
                + "当前实体类型: " + currentOutputType + "，可跳转方向：\n"
                + buildTraverseHint(traverseOptions) + "\n"
                + "典型用法：先筛选出高销量商品，再跳转到关联达人，分析哪些达人在带这些货。",
                schema(props(
                        prop("traverse_type", "string", "跳转路径，选择后数据流切换为目标实体", traverseOptions)
                ), List.of("traverse_type")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isHasDataSource()
                && !FieldDictionary.TRAVERSE_OPTIONS
                        .getOrDefault(session.getCurrentOutputType(), List.of()).isEmpty();
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String traverseType = (String) args.get("traverse_type");
        if (traverseType == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("traverse_type为必填参数")
                    .build();
        }

        if (!session.isHasDataSource()) {
            return McpObservation.builder()
                    .success(false)
                    .error("尚未选择数据源，请先调用 select_*_source 工具")
                    .build();
        }

        String[] blockInfo = FieldDictionary.TRAVERSE_BLOCK_MAP.get(traverseType);
        if (blockInfo == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("未知traverse_type: " + traverseType)
                    .build();
        }

        String blockId = blockInfo[0];
        String newOutputType = blockInfo[1];

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("traverse_type", traverseType);

        // hashtag_to_video 的 HashtagVideoTraverseRequest.region 为 required=true
        if ("hashtag_to_video".equals(traverseType)) {
            String region = (String) args.get("region");
            if (region == null) {
                return McpObservation.builder()
                        .success(false)
                        .error("hashtag_to_video跳转需要region参数（地区代码，如 TH、US）")
                        .build();
            }
            config.put("region", region.toUpperCase());
        }

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, blockId, config, traverseLabel(traverseType)));
        session.setSeqCounter(seq);

        // 切换数据类型和字段（优先用 blockId 精确字段集）
        session.setCurrentOutputType(newOutputType);
        List<String> traverseFields = FieldDictionary.getFieldsForBlockId(blockId);
        session.setAvailableFields(traverseFields.isEmpty()
                ? new ArrayList<>(FieldDictionary.getFieldsForType(newOutputType))
                : new ArrayList<>(traverseFields));
        session.setEstimatedRowCount(session.getEstimatedRowCount() * 3); // 跳转后数据量增加

        return McpObservation.builder()
                .success(true)
                .message("实体跳转完成: " + traverseLabel(traverseType) + "，数据类型变为 " + newOutputType)
                .chainLength(session.getBlocks().size())
                .dataType(newOutputType)
                .availableFields(session.getAvailableFields())
                .estimatedRows(session.getEstimatedRowCount())
                .scoreFields(session.getScoreFields())
                .hint("数据类型已变更为" + newOutputType + "，可用字段已更新。继续添加筛选或排序")
                .build();
    }

    private String buildTraverseHint(List<String> traverseOptions) {
        StringBuilder sb = new StringBuilder();
        for (String opt : traverseOptions) {
            sb.append("- ").append(opt).append(": ").append(traverseLabel(opt)).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private String traverseLabel(String traverseType) {
        return switch (traverseType) {
            case "product_to_influencer" -> "商品→达人";
            case "product_to_video" -> "商品→视频";
            case "product_to_comment" -> "商品→评论";
            case "influencer_to_product" -> "达人→商品";
            case "influencer_to_video" -> "达人→视频";
            case "video_to_product" -> "视频→商品";
            case "seller_to_product" -> "店铺→商品";
            case "hashtag_to_video" -> "话题→视频";
            default -> traverseType;
        };
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
