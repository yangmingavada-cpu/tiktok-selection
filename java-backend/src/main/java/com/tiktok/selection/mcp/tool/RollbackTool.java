package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.FieldDictionary;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * rollback 工具：回退积木链到指定步骤（增量模式）
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class RollbackTool implements McpTool {

    private static final String BLOCK_ID_KEY    = "blockId";
    private static final String SCORE_NUMERIC   = "SCORE_NUMERIC";
    private static final String SCORE_SEMANTIC  = "SCORE_SEMANTIC";

    @Override
    public String getToolName() {
        return "rollback";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        int maxStep = session.getBlocks().size();
        return tool("rollback",
                "回退积木链到指定步骤，删除该步骤之后的所有块（仅限增量模式）。\n"
                + "回退后available_fields和outputType将重建为目标步骤时的状态。\n"
                + "to_step范围: 1~" + maxStep + "（当前链长度），0无效。\n"
                + "示例：当前链有5步，to_step=3 表示保留前3步，删除第4、5步。",
                schema(props(
                        propReq("to_step", "integer",
                                "保留到第N步（含），范围1-" + maxStep + "，必须>=1，之后的块将被删除")
                ), List.of("to_step")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isIncrementalMode();
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        Object toStepObj = args.get("to_step");
        if (toStepObj == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("to_step为必填参数")
                    .build();
        }

        int toStep;
        if (toStepObj instanceof Number n) {
            toStep = n.intValue();
        } else {
            try { toStep = Integer.parseInt(String.valueOf(toStepObj)); }
            catch (NumberFormatException e) { toStep = -1; }
        }
        List<Map<String, Object>> blocks = session.getBlocks();

        if (toStep < 1 || toStep > blocks.size()) {
            return McpObservation.builder()
                    .success(false)
                    .error("to_step超出范围(1-" + blocks.size() + "): " + toStep)
                    .build();
        }

        List<Map<String, Object>> newBlocks = new ArrayList<>(blocks.subList(0, toStep));
        session.setBlocks(newBlocks);
        session.setSeqCounter(toStep);

        boolean stillHasDataSource = newBlocks.stream().anyMatch(b -> {
            String bid = (String) b.get(BLOCK_ID_KEY);
            return bid != null && FieldDictionary.SOURCE_TYPE_BLOCK_MAP.values()
                    .stream().anyMatch(arr -> arr[0].equals(bid));
        });
        session.setHasDataSource(stillHasDataSource);
        session.setEstimatedRowCount(0);

        rebuildFieldState(session, newBlocks);

        return McpObservation.builder()
                .success(true)
                .message("已回退到第" + toStep + "步，后续" + (blocks.size() - toStep) + "个块已删除")
                .chainLength(newBlocks.size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(session.getEstimatedRowCount())
                .scoreFields(session.getScoreFields())
                .hint("回退完成，可以重新添加新的块")
                .build();
    }

    // ── 私有方法 ────────────────────────────────────────────────────────────────

    private void rebuildFieldState(ChainBuildSession session, List<Map<String, Object>> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            String bid = (String) blocks.get(i).get(BLOCK_ID_KEY);
            if (bid == null || !isSourceOrTraverseBlock(bid)) continue;

            String outputType = findOutputTypeForBlock(bid);
            if (outputType != null) {
                session.setCurrentOutputType(outputType);
                List<String> bidFields = FieldDictionary.getFieldsForBlockId(bid);
                session.setAvailableFields(bidFields.isEmpty()
                        ? new ArrayList<>(FieldDictionary.getFieldsForType(outputType))
                        : new ArrayList<>(bidFields));
                session.setScoreFields(collectScoreFields(blocks));
                return;
            }
        }
    }

    private static boolean isSourceOrTraverseBlock(String bid) {
        return FieldDictionary.SOURCE_TYPE_BLOCK_MAP.values().stream().anyMatch(arr -> arr[0].equals(bid))
                || FieldDictionary.TRAVERSE_BLOCK_MAP.values().stream().anyMatch(arr -> arr[0].equals(bid));
    }

    @SuppressWarnings("unchecked")
    private static List<String> collectScoreFields(List<Map<String, Object>> blocks) {
        List<String> scoreFields = new ArrayList<>();
        for (Map<String, Object> b : blocks) {
            String bId = (String) b.get(BLOCK_ID_KEY);
            if (!SCORE_NUMERIC.equals(bId) && !SCORE_SEMANTIC.equals(bId)) continue;
            Map<String, Object> cfg = b.get("config") instanceof Map<?, ?>
                    ? (Map<String, Object>) b.get("config") : Map.of();
            String outField = (String) cfg.get("output_field");
            if (outField != null) scoreFields.add(outField);
        }
        return scoreFields;
    }

    private static String findOutputTypeForBlock(String blockId) {
        for (Map.Entry<String, String[]> entry : FieldDictionary.SOURCE_TYPE_BLOCK_MAP.entrySet()) {
            if (entry.getValue()[0].equals(blockId)) return entry.getValue()[1];
        }
        for (Map.Entry<String, String[]> entry : FieldDictionary.TRAVERSE_BLOCK_MAP.entrySet()) {
            if (entry.getValue()[0].equals(blockId)) return entry.getValue()[1];
        }
        return null;
    }
}
