package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.FieldDictionary;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * add_scoring 工具：添加评分维度
 * numeric → SC01（数值映射，零成本）
 * semantic → SC02（LLM语义评分）
 * 多个维度后自动追加SC00汇总器
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class AddScoringTool implements McpTool {

    @Override
    public String getToolName() {
        return "add_scoring";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        List<String> availableFields = session.getAvailableFields();
        return tool("add_scoring",
                "添加一个评分维度，可多次调用叠加多个维度。finalize_chain时自动生成加权汇总字段total_score（SC00块）。\n"
                + "scoring_type说明：\n"
                + "- numeric: 数值映射评分，将source_field的值线性/分段映射到[0,max_score]，零额外LLM成本，优先使用\n"
                + "  - algorithm=linear_map: 值越大得分越高（适合销量、GMV）\n"
                + "  - algorithm=tier_map: 分段给分（适合星级评分、佣金比例等离散值）\n"
                + "  - algorithm=inverse_map: 值越小得分越高（适合退款率、价格等）\n"
                + "- semantic: LLM语义评分，按semantic_prompt对每批≤5条数据调用LLM打分，消耗Token，仅用于无法用数值表达的维度（如'创意性'、'品牌调性'）\n"
                + "weight为各维度相对权重，所有维度权重之和无需等于100，系统自动归一化。",
                schema(props(
                        prop("scoring_type", "string", "评分类型", List.of("numeric", "semantic")),
                        propReq("dimension_name", "string",
                                "维度名称，如'销量表现'、'价格竞争力'、'创新性'，将作为字段名的一部分"),
                        propReq("weight", "integer", "相对权重(建议1-10)，与其他维度的weight对比决定占比"),
                        propOptDynEnum("source_field",
                                "numeric类型时：基于哪个字段的值进行评分，必须在available_fields中",
                                availableFields,
                                FieldDictionary.getFieldDescForType(session.getCurrentOutputType())),
                        propOpt("algorithm", "string", "numeric评分算法，默认linear_map",
                                List.of("linear_map", "tier_map", "inverse_map")),
                        propOpt("max_score", "integer", "单维度满分值，默认100"),
                        propOpt("semantic_prompt", "string",
                                "semantic类型时必填：描述评分标准，如'评估商品的创新性和差异化程度，满分100分，考虑产品独特性、外观设计、功能创新'")
                ), List.of("scoring_type", "dimension_name", "weight")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isHasDataSource() && "product_list".equals(session.getCurrentOutputType());
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String scoringType = (String) args.get("scoring_type");
        String dimensionName = (String) args.get("dimension_name");
        Object weightObj = args.get("weight");

        if (scoringType == null || dimensionName == null || weightObj == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("scoring_type、dimension_name、weight均为必填参数")
                    .build();
        }

        int weight = weightObj instanceof Number n ? n.intValue() : 5;
        String scoreField = "score_" + dimensionName.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("dimension_name", dimensionName);
        config.put("weight", weight);
        config.put("output_field", scoreField);
        config.put("max_score", args.getOrDefault("max_score", 100));

        String blockId;
        String label;

        if ("numeric".equals(scoringType)) {
            String sourceField = (String) args.get("source_field");
            String algorithm = (String) args.getOrDefault("algorithm", "linear_map");

            if (sourceField == null) {
                return McpObservation.builder()
                        .success(false)
                        .error("numeric类型评分需要source_field参数（评分依据字段名）")
                        .build();
            }
            if (!session.getAvailableFields().contains(sourceField)) {
                return McpObservation.builder()
                        .success(false)
                        .error("source_field'" + sourceField + "'不在当前可用字段中")
                        .build();
            }

            config.put("source_field", sourceField);
            config.put("algorithm", algorithm);
            blockId = "SCORE_NUMERIC";
            label = "数值评分: " + dimensionName + "(权重" + weight + ")";
        } else if ("semantic".equals(scoringType)) {
            String semanticPrompt = (String) args.get("semantic_prompt");
            if (semanticPrompt == null) {
                return McpObservation.builder()
                        .success(false)
                        .error("semantic类型需要semantic_prompt参数")
                        .build();
            }
            config.put("semantic_prompt", semanticPrompt);
            blockId = "SCORE_SEMANTIC";
            label = "语义评分: " + dimensionName + "(权重" + weight + ")";
        } else {
            return McpObservation.builder()
                    .success(false)
                    .error("scoring_type必须为'numeric'或'semantic'")
                    .build();
        }

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, blockId, config, label));
        session.setSeqCounter(seq);
        session.getAvailableFields().add(scoreField);
        session.getScoreFields().add(scoreField);

        // 第一个评分维度加入后，预注册 total_score 到 availableFields，
        // 使LLM可以在 add_sort 中引用 total_score（实际字段由 finalize_chain 生成）
        if (!session.getAvailableFields().contains("total_score")) {
            session.getAvailableFields().add("total_score");
        }

        int scoreCount = session.getScoreFields().size();
        String hint;
        if (scoreCount == 1) {
            hint = "已添加第1个评分维度。建议继续添加更多维度（numeric类型零成本），可用 total_score 字段排序取TopN，最后finalize_chain完成构建";
        } else {
            hint = "已添加第" + scoreCount + "个评分维度。可继续添加评分、用 total_score 排序取TopN，或finalize_chain完成构建";
        }

        return McpObservation.builder()
                .success(true)
                .message("已添加" + ("numeric".equals(scoringType) ? "数值" : "语义") + "评分维度: " + dimensionName)
                .chainLength(session.getBlocks().size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(session.getEstimatedRowCount())
                .scoreFields(session.getScoreFields())
                .hint(hint)
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
