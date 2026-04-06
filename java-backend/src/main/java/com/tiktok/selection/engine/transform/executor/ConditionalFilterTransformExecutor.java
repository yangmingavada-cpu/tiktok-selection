package com.tiktok.selection.engine.transform.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.ExecutorUtils;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.transform.request.ConditionalFilterTransformRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class ConditionalFilterTransformExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConditionalFilterTransformExecutor.class);
    private static final McpBlock BLOCK_META = ConditionalFilterTransformRequest.class.getAnnotation(McpBlock.class);

    private static final String OP_GT = ">";
    private static final String OP_GTE = ">=";
    private static final String OP_LT = "<";
    private static final String OP_LTE = "<=";
    private static final String OP_EQ = "==";
    private static final String OP_NEQ = "!=";
    private static final String OP_BETWEEN = "between";
    private static final String OP_IN = "in";
    private static final String OP_CONTAINS = "contains";

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
            ConditionalFilterTransformRequest req = ConditionalFilterTransformRequest.from(context.getBlockConfig());

            List<Map<String, Object>> outputData = new ArrayList<>();
            for (Map<String, Object> row : inputData) {
                Object fieldValue = row.get(req.field);
                if (matches(fieldValue, req.operator, req.value, req.value_to)) {
                    outputData.add(row);
                }
            }

            List<String> outputFields = context.getAvailableFields() != null
                ? new ArrayList<>(context.getAvailableFields()) : new ArrayList<>();

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, filtered {}->{}, field={}, op={}, duration={}ms",
                BLOCK_META.blockId(), inputCount, outputData.size(), req.field, req.operator, durationMs);

            return BlockResult.success(outputData, outputFields,
                context.getCurrentOutputType(), inputCount, outputData.size(), durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[{}] Execution failed: {}", BLOCK_META.blockId(), e.getMessage(), e);
            return BlockResult.fail(e.getMessage(), inputCount, durationMs);
        }
    }

    private boolean matches(Object fieldValue, String operator, Object value, Object valueTo) {
        if (fieldValue == null) {
            return false;
        }
        return switch (operator) {
            case OP_GT -> compareNumeric(fieldValue, value) > 0;
            case OP_GTE -> compareNumeric(fieldValue, value) >= 0;
            case OP_LT -> compareNumeric(fieldValue, value) < 0;
            case OP_LTE -> compareNumeric(fieldValue, value) <= 0;
            case OP_EQ -> equalsValue(fieldValue, value);
            case OP_NEQ -> !equalsValue(fieldValue, value);
            case OP_BETWEEN -> compareNumeric(fieldValue, value) >= 0
                && compareNumeric(fieldValue, valueTo) <= 0;
            case OP_IN -> matchesIn(fieldValue, value);
            case OP_CONTAINS -> fieldValue.toString().contains(value.toString());
            default -> {
                log.warn("[{}] Unknown operator: {}", BLOCK_META.blockId(), operator);
                yield false;
            }
        };
    }

    private int compareNumeric(Object a, Object b) {
        return Double.compare(ExecutorUtils.toDouble(a), ExecutorUtils.toDouble(b));
    }

    private boolean equalsValue(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (ExecutorUtils.isNumeric(a) && ExecutorUtils.isNumeric(b)) {
            return Double.compare(ExecutorUtils.toDouble(a), ExecutorUtils.toDouble(b)) == 0;
        }
        return a.toString().equals(b.toString());
    }

    @SuppressWarnings("unchecked")
    private boolean matchesIn(Object fieldValue, Object value) {
        if (value instanceof Collection) {
            return ((Collection<Object>) value).stream().anyMatch(item -> equalsValue(fieldValue, item));
        }
        String[] parts = value.toString().split(",");
        for (String part : parts) {
            if (equalsValue(fieldValue, part.trim())) return true;
        }
        return false;
    }
}
