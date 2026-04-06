package com.tiktok.selection.mcp.tool;

import com.tiktok.selection.mcp.ChainBuildSession;
import com.tiktok.selection.mcp.FieldDictionary;
import com.tiktok.selection.mcp.McpObservation;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.tiktok.selection.mcp.ToolSchemaHelper.*;

/**
 * add_computation 工具：添加计算衍生字段
 * computation_type → blockId映射:
 *   growth_rate  → CM01
 *   profit_margin → CM02
 *   daily_average → CM04
 *   custom_formula → CM04
 *   field_trim → FS01
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class AddComputationTool implements McpTool {

    private static final String INPUT_FIELD_A    = "input_field_a";
    private static final String INPUT_FIELD_B    = "input_field_b";
    private static final String OUTPUT_FIELD_NAME = "output_field_name";
    private static final String OUTPUT_FIELD_NAME_CAMEL = "outputFieldName";
    private static final String FORMULA          = "formula";

    @Override
    public String getToolName() {
        return "add_computation";
    }

    @Override
    public Map<String, Object> buildSchema(ChainBuildSession session) {
        List<String> availableFields = session.getAvailableFields();
        return tool("add_computation",
                "添加计算衍生字段，结果加入available_fields供后续筛选或评分使用。\n"
                + "各computation_type说明：\n"
                + "- growth_rate: 增长率计算，公式=(7日销量 - 30日销量/4.3)/(30日销量/4.3)×100，反映近期加速/减速趋势，需传input_field_a(7日字段)和input_field_b(30日字段)\n"
                + "- profit_margin: 估算利润率，基于均价×(1-30%成本系数)，需传input_field_a(价格字段)\n"
                + "- daily_average: 日均值，将累计销量除以天数，需传input_field_a(含天数的累计字段，如total_sale_30d_cnt)\n"
                + "- custom_formula: 自定义表达式，需传formula参数（如'total_sale_7d_cnt * 4.3'），支持 field OP field 或 field OP 常量，OP为+/-/*///\n"
                + "- field_trim: 字段裁剪，仅保留keep_fields中指定的字段，减少后续Token消耗，适合字段很多时精简",
                schema(props(
                        prop("computation_type", "string", "计算类型",
                                List.of("growth_rate", "profit_margin", "daily_average", "custom_formula", "field_trim")),
                        propDynEnum(INPUT_FIELD_A,
                                "主输入字段（growth_rate/profit_margin/daily_average时必填）",
                                availableFields,
                                FieldDictionary.getFieldDescForType(session.getCurrentOutputType())),
                        propOptDynEnum(INPUT_FIELD_B,
                                "次输入字段（growth_rate时传30日字段，如total_sale_30d_cnt）",
                                availableFields,
                                FieldDictionary.getFieldDescForType(session.getCurrentOutputType())),
                        propOpt(FORMULA, "string",
                                "custom_formula时必填：计算表达式，如 'total_sale_7d_cnt * 4.3' 或 'field_a / field_b'"),
                        propOpt(OUTPUT_FIELD_NAME, "string",
                                "输出字段名，不填则自动生成（如growth_rate_7d_30d）"),
                        propOptArrayDynEnum("keep_fields",
                                "field_trim时必填：保留的字段名列表，其余字段将被移除", availableFields)
                ), List.of("computation_type")));
    }

    @Override
    public boolean isAvailable(ChainBuildSession session) {
        return session.isHasDataSource() && "product_list".equals(session.getCurrentOutputType());
    }

    @Override
    public McpObservation execute(ChainBuildSession session, Map<String, Object> args) {
        String computationType = (String) args.get("computation_type");
        if (computationType == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("computation_type为必填参数")
                    .build();
        }

        return switch (computationType) {
            case "growth_rate" -> handleGrowthRate(session, args);
            case "profit_margin" -> handleProfitMargin(session, args);
            case "daily_average" -> handleDailyAverage(session, args);
            case "field_trim" -> handleFieldTrim(session, args);
            case "custom_formula" -> handleCustomFormula(session, args);
            default -> McpObservation.builder()
                    .success(false)
                    .error("未知computation_type: " + computationType)
                    .build();
        };
    }

    private McpObservation handleGrowthRate(ChainBuildSession session, Map<String, Object> args) {
        String fieldA = (String) args.getOrDefault(INPUT_FIELD_A, "total_sale_7d_cnt");
        String fieldB = (String) args.getOrDefault(INPUT_FIELD_B, "total_sale_30d_cnt");
        String outputName = (String) args.getOrDefault(OUTPUT_FIELD_NAME, "growth_rate_7d_30d");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("field_a", fieldA);
        config.put("field_b", fieldB);
        config.put(OUTPUT_FIELD_NAME, outputName);
        // CM01公式: (A - B/4.3) / (B/4.3) × 100
        config.put(FORMULA, "growth_rate");

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, "COMPUTE_GROWTH_RATE", config, "计算增长率: " + outputName));
        session.setSeqCounter(seq);
        session.getAvailableFields().add(outputName);

        return successObs(session, "已添加增长率计算字段: " + outputName,
                "可继续添加筛选或将" + outputName + "用于评分");
    }

    private McpObservation handleProfitMargin(ChainBuildSession session, Map<String, Object> args) {
        String priceField = (String) args.getOrDefault(INPUT_FIELD_A, "spu_avg_price");
        String costField  = (String) args.get(INPUT_FIELD_B);
        String outputName = (String) args.getOrDefault(OUTPUT_FIELD_NAME, "profit_margin_est");

        if (costField == null) {
            return McpObservation.builder()
                    .success(false)
                    .error("profit_margin需要input_field_b参数指定成本字段名（如 'cost_price'）")
                    .build();
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("price_field", priceField);
        config.put("cost_field", costField);
        config.put(OUTPUT_FIELD_NAME_CAMEL, outputName);

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, "COMPUTE_PROFIT_MARGIN", config, "利润率: " + outputName));
        session.setSeqCounter(seq);
        session.getAvailableFields().add(outputName);

        return successObs(session, "已添加利润率计算字段: " + outputName,
                "利润率 = (" + priceField + " - " + costField + ") / " + priceField + " × 100。可用于筛选或评分");
    }

    private McpObservation handleDailyAverage(ChainBuildSession session, Map<String, Object> args) {
        String inputField = (String) args.get(INPUT_FIELD_A);
        String outputName = (String) args.get(OUTPUT_FIELD_NAME);

        if (inputField == null) {
            inputField = "total_sale_30d_cnt";
        }
        if (outputName == null) {
            outputName = "daily_avg_" + inputField.replace("total_sale_", "").replace("_cnt", "");
        }

        // 从字段名提取天数，如 total_sale_30d_cnt → 30，生成 CM04 能解析的真实表达式
        String dayExtracted = inputField.replaceAll(".*?(\\d+)d.*", "$1");
        int days;
        try {
            days = Integer.parseInt(dayExtracted);
        } catch (NumberFormatException e) {
            return McpObservation.builder()
                    .success(false)
                    .error("daily_average无法从字段名'" + inputField + "'提取天数，建议改用 custom_formula 自定义公式")
                    .build();
        }
        String formulaExpr = inputField + " / " + days;

        Map<String, Object> config = new LinkedHashMap<>();
        config.put(FORMULA, formulaExpr);
        config.put(OUTPUT_FIELD_NAME_CAMEL, outputName);

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, "COMPUTE_FORMULA", config, "日均: " + outputName));
        session.setSeqCounter(seq);
        session.getAvailableFields().add(outputName);

        return successObs(session, "已添加日均计算字段: " + outputName,
                "日均值 = " + inputField + " / 天数。可用于筛选或评分");
    }

    private McpObservation handleFieldTrim(ChainBuildSession session, Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        List<String> keepFields = (List<String>) args.get("keep_fields");

        if (keepFields == null || keepFields.isEmpty()) {
            return McpObservation.builder()
                    .success(false)
                    .error("field_trim需要keep_fields参数（要保留的字段列表）")
                    .build();
        }

        // 取交集
        List<String> validFields = keepFields.stream()
                .filter(f -> session.getAvailableFields().contains(f))
                .toList();

        if (validFields.isEmpty()) {
            return McpObservation.builder()
                    .success(false)
                    .error("keep_fields中没有有效字段（与当前available_fields无交集）")
                    .build();
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("keep_fields", validFields);

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, "TRANSFORM_FIELD_TRIM", config, "字段裁剪（保留" + validFields.size() + "个字段）"));
        session.setSeqCounter(seq);
        session.setAvailableFields(new ArrayList<>(validFields));

        return successObs(session, "已裁剪字段，现有" + validFields.size() + "个字段",
                "字段已精简，可继续添加评分或排序");
    }

    private McpObservation handleCustomFormula(ChainBuildSession session, Map<String, Object> args) {
        String formula = (String) args.get(FORMULA);
        String outputName = (String) args.getOrDefault(OUTPUT_FIELD_NAME, "custom_field");

        if (formula == null || formula.isBlank()) {
            return McpObservation.builder()
                    .success(false)
                    .error("custom_formula需要formula参数，如 'total_sale_7d_cnt * 4.3'")
                    .build();
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put(FORMULA, formula);
        config.put(OUTPUT_FIELD_NAME_CAMEL, outputName);

        int seq = session.getSeqCounter() + 1;
        session.getBlocks().add(buildBlock(seq, "COMPUTE_FORMULA", config, "自定义计算: " + outputName));
        session.setSeqCounter(seq);
        session.getAvailableFields().add(outputName);

        return successObs(session, "已添加自定义计算字段: " + outputName, null);
    }

    private McpObservation successObs(ChainBuildSession session, String message, String hint) {
        return McpObservation.builder()
                .success(true)
                .message(message)
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
