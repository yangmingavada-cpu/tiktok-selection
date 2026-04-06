package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * add_comment 工具：添加AI评语（映射为LA01块）
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class AddCommentTool implements McpTool {

    @Override
    public String getToolName() {
        return "add_comment";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("add_comment",
                "为结果列表中每条数据生成AI评语（LLM驱动，每批≤5条调用一次LLM，消耗Token）。\n"
                + "评语基于当前所有可用字段（含评分字段）生成，内容涵盖选品理由、优势、风险点等。\n"
                + "通常放在add_sort之后、finalize_chain之前，此时数据量已缩减到最终TopN，Token消耗可控。",
                schema(props(
                        prop("language", "string", "评语语言：zh=中文（适合中国卖家），en=英文",
                                List.of("zh", "en")),
                        propOpt("max_chars", "integer",
                                "每条评语最大字符数，默认100字。建议50-200范围内，过长消耗Token过多")
                ), List.of("language")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        String t = session.getCurrentOutputType();
        return session.isHasDataSource()
                && ("product_list".equals(t) || "scored_list".equals(t));
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String language = (String) args.getOrDefault("language", "zh");
        int maxChars = args.get("max_chars") instanceof Number n ? n.intValue() : 100;

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("language", language);
        config.put("max_chars", maxChars);

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, "ANNOTATE_LLM_COMMENT", config,
                "AI评语(" + ("zh".equals(language) ? "中文" : "英文") + ")"));
        session.setSeqCounter(seq);
        session.getAvailableFields().add("ai_comment");

        return McpObservation.builder()
                .success(true)
                .message("已添加AI评语块，将为每个商品生成" + maxChars + "字以内的" +
                        ("zh".equals(language) ? "中文" : "英文") + "评语")
                .chainLength(session.getBlocks().size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(session.getEstimatedRowCount())
                .scoreFields(session.getScoreFields())
                .hint("AI评语已添加。可以finalize_chain完成积木链构建")
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
