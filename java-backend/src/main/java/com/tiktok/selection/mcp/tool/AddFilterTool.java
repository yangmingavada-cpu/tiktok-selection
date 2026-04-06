package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.FieldDictionary;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * add_filter 工具：添加筛选条件（映射为FT01块）
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class AddFilterTool implements McpTool {

    @Override
    public String getToolName() {
        return "add_filter";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        return tool("add_filter",
                "添加一条筛选条件（对数据行按字段值过滤）。\n"
                + "可多次调用，多个条件之间是AND（全部满足）关系。\n"
                + "field必须来自当前available_fields，传入不存在的字段会报错。\n"
                + "operator使用建议：数值字段用 >/</>=/<=；枚举字段（如sales_trend_flag）用 ==；范围用 between，值为[min,max]；多值匹配用 in，值为数组。",
                schema(props(
                        propDynEnum("field", "筛选字段，必须在当前available_fields中",
                                session.getAvailableFields(),
                                FieldDictionary.getFieldDescForType(session.getCurrentOutputType())),
                        prop("operator", "string", "比较运算符",
                                List.of(">", ">=", "<", "<=", "==", "!=", "between", "in")),
                        propAny("value",
                                "比较值。between时传[min,max]数组如[10,100]；in时传值数组如[1,2,3]；其他传单个值")
                ), List.of("field", "operator", "value")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isHasDataSource();
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String field = (String) args.get("field");
        String operator = (String) args.get("operator");
        Object value = args.get("value");

        if (!session.isHasDataSource()) {
            return McpObservation.builder()
                    .success(false)
                    .error("尚未选择数据源，请先调用 select_*_source 工具")
                    .build();
        }

        if (field == null || operator == null || value == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("field、operator、value均为必填参数")
                    .build();
        }

        // between 算子要求 value 为 [min, max] 两元素数组
        if ("between".equals(operator) && (!(value instanceof List<?> list) || list.size() < 2)) {
            return McpObservation.builder()
                    .success(false)
                    .error("between运算符的value必须为 [min, max] 两元素数组")
                    .build();
        }

        // 校验字段是否在available_fields中
        if (!session.getAvailableFields().contains(field)) {
            return McpObservation.builder()
                    .success(false)
                    .error("字段'" + field + "'不在当前可用字段中。可用字段: " + session.getAvailableFields())
                    .build();
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("field", field);
        config.put("operator", operator);
        config.put("value", value);

        int seq = session.getSeqCounter() + 1;
        Map<String, Object> block = buildBlock(seq, "FILTER_CONDITION", config,
                "筛选: " + field + " " + operator + " " + value);
        session.getBlocks().add(block);
        session.setSeqCounter(seq);

        // 筛选后估算行数减少
        int newEstimate = Math.max(1, session.getEstimatedRowCount() / 3);
        session.setEstimatedRowCount(newEstimate);

        String hint = "筛选已添加，当前估算约" + newEstimate + "条。可继续筛选、添加评分或排序取TopN";

        return McpObservation.builder()
                .success(true)
                .message("已添加筛选: " + field + " " + operator + " " + value)
                .chainLength(session.getBlocks().size())
                .dataType(session.getCurrentOutputType())
                .availableFields(session.getAvailableFields())
                .estimatedRows(newEstimate)
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
