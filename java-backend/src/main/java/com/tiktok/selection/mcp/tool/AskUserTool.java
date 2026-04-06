package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * ask_user 工具：在规划阶段主动询问用户，返回 needs_input 信号
 * Agent 收到此 Observation 后不再继续规划，等待用户输入后重新触发 /intent/parse
 *
 * @author system
 * @date 2026/03/27
 */
@Component
public class AskUserTool implements McpTool {

    @Override
    public String getToolName() {
        return "ask_user";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("ask_user",
                "向用户询问缺失的关键信息，暂停规划等待用户回复。\n"
                + "当用户需求中缺少地区、品类、筛选条件等必要信息时，必须先调用此工具，不得自行假设。\n"
                + "调用后规划立即暂停，用户回复后继续。",
                schema(props(
                        propReq("message", "string", "向用户提出的问题，如'您希望选哪个目标市场？'"),
                        propOptArrayStr("suggestions",
                                "给用户的建议选项列表（2-4条），如['泰国TH','印尼ID','美国US']")
                ), List.of("message")));
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String message = args.get("message") instanceof String s ? s : "请补充信息以继续规划";
        List<String> suggestions = args.get("suggestions") instanceof List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();

        return McpObservation.builder()
                .success(true)
                .type("needs_input")
                .message(message)
                .suggestions(suggestions)
                .build();
    }
}
