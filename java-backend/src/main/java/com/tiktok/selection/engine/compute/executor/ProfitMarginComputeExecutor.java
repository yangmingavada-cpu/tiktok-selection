package com.tiktok.selection.engine.compute.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.ExecutorUtils;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.compute.request.ProfitMarginComputeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CM02 - 利润率计算 (Profit Margin Calculation).
 * Pure computation block: (price - cost) / price * 100.
 *
 * @author system
 * @date 2026/03/22
 */
@Component
public class ProfitMarginComputeExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProfitMarginComputeExecutor.class);
    private static final McpBlock BLOCK_META = ProfitMarginComputeRequest.class.getAnnotation(McpBlock.class);
    private static final double PERCENTAGE_MULTIPLIER = 100.0;

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
            ProfitMarginComputeRequest req = ProfitMarginComputeRequest.from(context.getBlockConfig());
            String outputFieldName = req.getEffectiveOutputField();

            List<Map<String, Object>> outputData = new ArrayList<>(inputData.size());

            for (Map<String, Object> row : inputData) {
                Map<String, Object> newRow = new HashMap<>(row);
                double price = ExecutorUtils.toDouble(row.get(req.priceField));
                double cost = ExecutorUtils.toDouble(row.get(req.costField));

                double profitMargin;
                if (Double.compare(price, 0.0) == 0) {
                    profitMargin = 0;
                } else {
                    profitMargin = (price - cost) / price * PERCENTAGE_MULTIPLIER;
                }
                newRow.put(outputFieldName, Math.round(profitMargin * 100.0) / 100.0);
                outputData.add(newRow);
            }

            List<String> outputFields = context.getAvailableFields() != null
                ? new ArrayList<>(context.getAvailableFields()) : new ArrayList<>();
            if (!outputFields.contains(outputFieldName)) {
                outputFields.add(outputFieldName);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, rows={}, newField={}, duration={}ms",
                BLOCK_META.blockId(), outputData.size(), outputFieldName, durationMs);

            return BlockResult.success(outputData, outputFields,
                context.getCurrentOutputType(), inputCount, outputData.size(), durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[{}] Execution failed: {}", BLOCK_META.blockId(), e.getMessage(), e);
            return BlockResult.fail(e.getMessage(), inputCount, durationMs);
        }
    }
}
