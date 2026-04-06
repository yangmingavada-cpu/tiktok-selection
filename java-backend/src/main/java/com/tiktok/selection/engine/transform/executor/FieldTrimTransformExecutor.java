package com.tiktok.selection.engine.transform.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.transform.request.FieldTrimTransformRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FieldTrimTransformExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(FieldTrimTransformExecutor.class);
    private static final McpBlock BLOCK_META = FieldTrimTransformRequest.class.getAnnotation(McpBlock.class);

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
            FieldTrimTransformRequest req = FieldTrimTransformRequest.from(context.getBlockConfig());

            List<String> available = context.getAvailableFields() != null
                ? context.getAvailableFields() : Collections.emptyList();

            List<String> keepFields = (req.keepFields != null && !req.keepFields.isEmpty())
                ? req.keepFields : new ArrayList<>(available);

            List<String> effectiveFields = keepFields.stream()
                .filter(available::contains)
                .collect(Collectors.toList());

            List<Map<String, Object>> outputData = new ArrayList<>(inputData.size());
            for (Map<String, Object> row : inputData) {
                Map<String, Object> trimmed = new HashMap<>();
                for (String field : effectiveFields) {
                    if (row.containsKey(field)) {
                        trimmed.put(field, row.get(field));
                    }
                }
                outputData.add(trimmed);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, fields trimmed to {}, duration={}ms",
                BLOCK_META.blockId(), effectiveFields.size(), durationMs);

            return BlockResult.success(outputData, effectiveFields,
                context.getCurrentOutputType(), inputCount, outputData.size(), durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[{}] Execution failed: {}", BLOCK_META.blockId(), e.getMessage(), e);
            return BlockResult.fail(e.getMessage(), inputCount, durationMs);
        }
    }
}
