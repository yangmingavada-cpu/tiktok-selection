package com.tiktok.selection.engine.compute.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.ExecutorUtils;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.compute.request.CustomFormulaComputeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CM04 - 自定义公式 (Custom Formula).
 * Pure computation block: "field_a OP field_b" or "field_a OP constant".
 *
 * @author system
 * @date 2026/03/22
 */
@Component
public class CustomFormulaComputeExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(CustomFormulaComputeExecutor.class);
    private static final McpBlock BLOCK_META = CustomFormulaComputeRequest.class.getAnnotation(McpBlock.class);

    private static final Pattern FORMULA_PATTERN = Pattern.compile(
        "^\\s*([\\w.]+)\\s*([+\\-*/])\\s*([\\w.]+)\\s*$");

    @Override
    public String getBlockId() {
        return BLOCK_META.blockId();
    }

    @Override
    public BlockResult execute(BlockContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("[{}] Execution started, sessionId={}", BLOCK_META.blockId(), context.getSessionId());

        List<Map<String, Object>> inputData =
            context.getInputData() != null ? context.getInputData() : Collections.emptyList();
        Integer inputCount = inputData.size();

        try {
            CustomFormulaComputeRequest req = CustomFormulaComputeRequest.from(context.getBlockConfig());
            String formula = req.formula;
            String outputFieldName = req.outputFieldName;

            if (formula == null || formula.isBlank()) {
                throw new IllegalArgumentException("Formula is required for CM04");
            }
            if (outputFieldName == null || outputFieldName.isBlank()) {
                throw new IllegalArgumentException("outputFieldName is required for CM04");
            }

            Matcher matcher = FORMULA_PATTERN.matcher(formula);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                    "Unsupported formula format: " + formula + ". Expected: operand1 OP operand2");
            }

            String leftOperand = matcher.group(1);
            String operator = matcher.group(2);
            String rightOperand = matcher.group(3);

            boolean rightIsConstant = isNumericConstant(rightOperand);
            boolean leftIsConstant = isNumericConstant(leftOperand);

            List<Map<String, Object>> outputData = new ArrayList<>(inputData.size());

            for (Map<String, Object> row : inputData) {
                Map<String, Object> newRow = new HashMap<>(row);
                double leftVal = leftIsConstant
                    ? Double.parseDouble(leftOperand)
                    : ExecutorUtils.toDouble(row.get(leftOperand));
                double rightVal = rightIsConstant
                    ? Double.parseDouble(rightOperand)
                    : ExecutorUtils.toDouble(row.get(rightOperand));

                double result = compute(leftVal, operator, rightVal);
                newRow.put(outputFieldName, Math.round(result * 100.0) / 100.0);
                outputData.add(newRow);
            }

            List<String> outputFields = context.getAvailableFields() != null
                ? new ArrayList<>(context.getAvailableFields()) : new ArrayList<>();
            if (!outputFields.contains(outputFieldName)) {
                outputFields.add(outputFieldName);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, formula='{}', rows={}, duration={}ms",
                BLOCK_META.blockId(), formula, outputData.size(), durationMs);

            return BlockResult.success(outputData, outputFields,
                context.getCurrentOutputType(), inputCount, outputData.size(), durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[{}] Execution failed: {}", BLOCK_META.blockId(), e.getMessage(), e);
            return BlockResult.fail(e.getMessage(), inputCount, durationMs);
        }
    }

    private double compute(double left, String operator, double right) {
        return switch (operator) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> Double.compare(right, 0.0) == 0 ? 0 : left / right;
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    private boolean isNumericConstant(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
