package com.tiktok.selection.engine.output.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class FinalOutputExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(FinalOutputExecutor.class);

    private static final String BLOCK_ID = "OUTPUT_FINAL";
    private static final String META_TOTAL_COUNT = "total_count";
    private static final String META_FIELD_COUNT = "field_count";
    private static final String META_OUTPUT_TYPE = "output_type";

    @Override
    public String getBlockId() {
        return BLOCK_ID;
    }

    @Override
    public BlockResult execute(BlockContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("[{}] Execution started, sessionId={}", BLOCK_ID, context.getSessionId());

        List<Map<String, Object>> inputData =
            context.getInputData() != null ? context.getInputData() : Collections.emptyList();
        Integer inputCount = inputData.size();

        try {
            List<String> outputFields = context.getAvailableFields() != null
                ? new ArrayList<>(context.getAvailableFields()) : new ArrayList<>();

            String outputType = context.getCurrentOutputType();
            long durationMs = System.currentTimeMillis() - startTime;

            BlockResult result = BlockResult.success(
                inputData, outputFields, outputType, inputCount, inputData.size(), durationMs);

            result.getMetadata().put(META_TOTAL_COUNT, inputData.size());
            result.getMetadata().put(META_FIELD_COUNT, outputFields.size());
            result.getMetadata().put(META_OUTPUT_TYPE, outputType);

            log.info("[{}] Execution completed, totalCount={}, fieldCount={}, outputType={}, duration={}ms",
                BLOCK_ID, inputData.size(), outputFields.size(), outputType, durationMs);

            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[{}] Execution failed: {}", BLOCK_ID, e.getMessage(), e);
            return BlockResult.fail(e.getMessage(), inputCount, durationMs);
        }
    }
}
