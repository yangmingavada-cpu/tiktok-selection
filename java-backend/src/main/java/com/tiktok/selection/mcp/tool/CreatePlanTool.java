package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * create_plan 工具：主 Agent 澄清完用户意图后提交规划草稿，等待用户确认。
 *
 * <p>工具流程：
 * <ol>
 *   <li>主 Agent 调用本工具，传入 SelectionPlan 结构体</li>
 *   <li>工具将规划存入 {@link ChainBuildSession#setSelectionPlan(Map)}，
 *       返回 {@code type=plan_draft} 的 Observation</li>
 *   <li>Python Agent 识别 plan_draft，停止当前轮次并返回给 Java</li>
 *   <li>Java 将规划展示给前端，用户确认后调用 {@code POST /api/intent/confirm-plan}</li>
 *   <li>Java 以 "plan_confirmed" 触发第二轮 Agent，开始构建 block_chain</li>
 * </ol>
 *
 * @author system
 * @date 2026/04/05
 */
@Component
public class CreatePlanTool implements McpTool {

    @Override
    public String getToolName() {
        return "create_plan";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("create_plan",
                "提交选品规划草稿，等待用户确认后再开始构建积木链。"
                + "在完整收集用户需求后、开始构建之前调用此工具。",
                schema(props(
                        propReq("market",       "string", "目标市场/地区（如：泰国、印尼、美国、英国、马来西亚、越南、菲律宾、新加坡、沙特、阿联酋）"),
                        propReq("category",     "string", "商品品类（如：家居、3C、美妆）"),
                        propReq("price_range",  "string", "价格区间（如：10-50美元、不限）"),
                        propOpt("filters",      "string", "核心筛选条件描述（如：近30天销量≥10、评分≥4.0）"),
                        propOpt("scoring_dimensions", "string", "评分维度与权重（如：销量增长40%、评分30%、利润30%）"),
                        propOpt("output_count", "integer", "最终推荐商品数量（如：20、50、100）"),
                        propOpt("strategy_notes", "string", "策略备注（用户额外要求或注意事项）")
                ), List.of("market", "category", "price_range")));
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("market",             args.get("market"));
        plan.put("category",           args.get("category"));
        plan.put("price_range",        args.get("price_range"));
        plan.put("filters",            args.getOrDefault("filters", ""));
        plan.put("scoring_dimensions", args.getOrDefault("scoring_dimensions", ""));
        plan.put("output_count",       args.getOrDefault("output_count", 20));
        plan.put("strategy_notes",     args.getOrDefault("strategy_notes", ""));

        // 存入会话，供 confirm-plan 端点读取
        session.setSelectionPlan(plan);

        String summary = String.format("目标市场：%s | 品类：%s | 价格：%s | 推荐数量：%s",
                plan.get("market"), plan.get("category"),
                plan.get("price_range"), plan.get("output_count"));

        return McpObservation.builder()
                .success(true)
                .type("plan_draft")
                .selectionPlan(plan)
                .message("选品规划草稿已提交，等待用户确认后开始构建：" + summary)
                .build();
    }
}
