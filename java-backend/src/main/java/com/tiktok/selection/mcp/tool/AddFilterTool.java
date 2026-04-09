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
                "添加一条筛选条件（对数据行按字段值过滤）。可多次调用，条件之间为AND关系。\n"
                + "field必须来自当前available_fields。\n"
                + "【推荐】范围筛选请分两次调用 >= 和 <=，例如价格50-100元：\n"
                + "  第1次: field=\"spu_avg_price\", operator=\">=\", value=50\n"
                + "  第2次: field=\"spu_avg_price\", operator=\"<=\", value=100\n"
                + "operator说明：>=/<=/>/< 用于数值范围（分两次调用）；== 用于精确匹配；!= 用于排除；in 用于多值匹配（value传数组如[1,2,3]）。\n"
                + "高级：between运算符value必须传JSON数组[min,max]，但推荐用 >= + <= 替代更不易出错。",
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
            String missing = (field == null ? "field " : "")
                    + (operator == null ? "operator " : "")
                    + (value == null ? "value" : "");
            return McpObservation.builder()
                    .success(false)
                    .error("缺少必填参数: " + missing.trim()
                            + "。示例: field=\"spu_avg_price\", operator=\">=\", value=50")
                    .build();
        }

        // between 算子：尝试自动修正常见错误格式
        if ("between".equals(operator)) {
            if (value instanceof String str) {
                String[] parts = str.split("[-,]");
                if (parts.length == 2) {
                    try {
                        value = List.of(Double.parseDouble(parts[0].trim()),
                                        Double.parseDouble(parts[1].trim()));
                        args.put("value", value);
                    } catch (NumberFormatException ignored) { /* fall through */ }
                }
            }
            if (!(value instanceof List<?> list) || list.size() < 2) {
                return McpObservation.builder()
                        .success(false)
                        .error("between的value必须为[min,max]数组，如[50,100]。推荐改用两次调用: >= 50 和 <= 100")
                        .build();
            }
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
