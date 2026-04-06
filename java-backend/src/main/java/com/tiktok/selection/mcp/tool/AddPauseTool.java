package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * add_pause 工具：添加暂停点（映射为SS01块）
 * 执行到此处时暂停，等用户确认后继续
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class AddPauseTool implements McpTool {

    @Override
    public String getToolName() {
        return "add_pause";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("add_pause",
                "在积木链中插入一个暂停检查点。执行到此处时自动暂停，显示当前数据供用户审查，用户确认后方可继续执行后续块。\n"
                + "适合用于：筛选完成后让用户确认数据质量、评分完成后让用户审核结果再决定是否继续。",
                schema(props(
                        propOpt("pause_message", "string",
                                "暂停时展示给用户的提示文字，如'请确认筛选结果后点击继续'")
                ), List.of()));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isHasDataSource();
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String pauseMessage = (String) args.getOrDefault("pause_message",
                "执行已暂停，请查看当前数据后决定是否继续");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("pause_message", pauseMessage);

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, "CONTROL_PAUSE", config, "暂停检查点"));
        session.setSeqCounter(seq);

        return McpObservation.builder()
                .success(true)
                .message("已添加暂停点，执行到此处将暂停等待用户确认")
                .chainLength(session.getBlocks().size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(session.getEstimatedRowCount())
                .scoreFields(session.getScoreFields())
                .hint("暂停点已添加。用户可在此处审查数据后决定继续或调整参数。继续添加后续块或finalize_chain")
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
