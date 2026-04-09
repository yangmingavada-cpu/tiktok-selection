package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * finalize_chain 工具：完成积木链构建
 * 1. 如果有评分维度，自动追加SC00汇总器
 * 2. 自动追加OUT01输出块
 * 3. 返回完整block_chain
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class FinalizeChainTool implements McpTool {

    @Override
    public String getToolName() {
        return "finalize_chain";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("finalize_chain",
                "完成积木链构建，返回最终block_chain供用户确认执行。调用此工具后LLM任务结束。\n"
                + "自动行为：\n"
                + "1. 若链中有评分维度（SC01/SC02），自动追加SC00评分汇总块，生成total_score字段\n"
                + "2. 自动追加OUT01输出块\n"
                + "3. 对所有块重新编号确保seq连续\n"
                + "在满足用户需求后即可调用，无需等待所有可能的工具都用过。",
                schema(props(
                        propOpt("summary", "string",
                                "积木链的简短说明，如'泰国家居类高增长商品选品，按综合评分取Top50'")
                ), List.of()));
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String summary = (String) args.getOrDefault("summary", "AI生成的选品积木链");

        List<Map<String, Object>> blocks = session.getBlocks();

        // 如果有评分维度但没有SC00汇总器，自动追加
        boolean hasScoreDimensions = !session.getScoreFields().isEmpty();
        boolean hasSc00 = blocks.stream()
                .anyMatch(b -> "SCORE_AGGREGATE".equals(b.get("blockId")));

        if (hasScoreDimensions && !hasSc00) {
            // 收集所有评分维度配置
            List<Map<String, Object>> dimensions = new ArrayList<>();
            for (Map<String, Object> block : blocks) {
                String bid = (String) block.get("blockId");
                if ("SCORE_NUMERIC".equals(bid) || "SCORE_SEMANTIC".equals(bid)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> config = (Map<String, Object>) block.get("config");
                    dimensions.add(Map.of(
                            "dimension_name", config.getOrDefault("dimension_name", "dim"),
                            "output_field", config.getOrDefault("output_field", "score"),
                            "weight", config.getOrDefault("weight", 1)
                    ));
                }
            }

            Map<String, Object> sc00Config = new LinkedHashMap<>();
            sc00Config.put("dimensions", dimensions);
            sc00Config.put("output_field", "total_score");

            // 插入到最后一个 SCORE_NUMERIC/SCORE_SEMANTIC 之后
            // 确保所有评分维度都已计算完再汇总（修复多实体链中的顺序错误）
            int lastScoreIdx = -1;
            for (int i = 0; i < blocks.size(); i++) {
                String bid = (String) blocks.get(i).get("blockId");
                if ("SCORE_NUMERIC".equals(bid) || "SCORE_SEMANTIC".equals(bid)) {
                    lastScoreIdx = i;
                }
            }
            int insertIdx = (lastScoreIdx >= 0) ? lastScoreIdx + 1 : blocks.size();
            blocks.add(insertIdx, buildBlock(0, "SCORE_AGGREGATE", sc00Config, "评分汇总(total_score)"));

            if (!session.getAvailableFields().contains("total_score")) {
                session.getAvailableFields().add("total_score");
            }
            if (!session.getScoreFields().contains("total_score")) {
                session.getScoreFields().add("total_score");
            }
        }

        // 检查是否已有OUT01，没有则追加
        boolean hasOut = blocks.stream()
                .anyMatch(b -> "OUTPUT_FINAL".equals(b.get("blockId")));

        if (!hasOut) {
            Map<String, Object> outConfig = new LinkedHashMap<>();
            outConfig.put("summary", summary);

            int outSeq = session.getSeqCounter() + 1;
            blocks.add(buildBlock(outSeq, "OUTPUT_FINAL", outConfig, "输出结果"));
            session.setSeqCounter(outSeq);
        }

        // 重新编号确保seq连续
        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).put("seq", i + 1);
        }

        return McpObservation.builder()
                .success(true)
                .message("积木链构建完成，共" + blocks.size() + "个块")
                .chainLength(blocks.size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(session.getEstimatedRowCount())
                .scoreFields(session.getScoreFields())
                .blockChain(new ArrayList<>(blocks))
                .hint("积木链已完成，可提交执行")
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
