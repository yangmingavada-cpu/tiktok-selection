package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * create_branch 工具：创建分支积木链（增量模式）
 * 标记当前位置为分支起点，后续块将在分支上添加
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class CreateBranchTool implements McpTool {

    @Override
    public String getToolName() {
        return "create_branch";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("create_branch",
                "在当前位置创建一个分支标记（仅限增量模式）。\n"
                + "后续在分支上添加的块与主链独立执行，最终用户可比较主链和分支的结果，选择更优的方案。",
                schema(props(
                        propReq("branch_purpose", "string", "分支目的描述，如'尝试按GMV排序的替代方案'")
                ), List.of("branch_purpose")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isIncrementalMode();
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String branchPurpose = (String) args.getOrDefault("branch_purpose", "探索性分支");

        // 在当前位置插入分支标记块
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("branch_purpose", branchPurpose);
        config.put("branch_from_step", session.getSeqCounter());

        int seq = session.getSeqCounter() + 1;
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("seq", seq);
        block.put("blockId", "SS02_BRANCH");
        block.put("label", "分支: " + branchPurpose);
        block.put("config", config);
        session.getBlocks().add(block);
        session.setSeqCounter(seq);

        return McpObservation.builder()
                .success(true)
                .message("分支创建成功: " + branchPurpose + "。后续块将在此分支上执行")
                .chainLength(session.getBlocks().size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(session.getEstimatedRowCount())
                .scoreFields(session.getScoreFields())
                .hint("分支已创建，可继续添加块。分支执行结果与主链独立，用户可比较后选择")
                .build();
    }
}
