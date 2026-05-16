package com.tiktok.selection.engine.transform.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.ExecutorUtils;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.transform.request.SortTopNTransformRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SortTopNTransformExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(SortTopNTransformExecutor.class);
    private static final McpBlock BLOCK_META = SortTopNTransformRequest.class.getAnnotation(McpBlock.class);
    private static final String ORDER_ASC = "asc";

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
            SortTopNTransformRequest req = SortTopNTransformRequest.from(context.getBlockConfig());
            String sortBy = req.sort_by;
            String order = req.getEffectiveOrder();
            int topN = req.getEffectiveTopN();

            List<Map<String, Object>> sorted = new ArrayList<>(inputData);

            Comparator<Map<String, Object>> comparator =
                (a, b) -> compareValues(a.get(sortBy), b.get(sortBy));

            if (!ORDER_ASC.equalsIgnoreCase(order)) {
                comparator = comparator.reversed();
            }
            sorted.sort(comparator);

            // 兜底去重：SortTopNTransformRequest.deduplicate 默认 true。
            // 即使 SOURCE_PRODUCT_LIST 已去重，这里再扫一遍防止其他来源/拼接路径漏网。
            List<Map<String, Object>> deduped;
            if (Boolean.FALSE.equals(req.deduplicate)) {
                deduped = sorted;
            } else {
                Set<String> seen = HashSet.newHashSet(sorted.size());
                deduped = new ArrayList<>(sorted.size());
                for (Map<String, Object> row : sorted) {
                    Object pid = row.get("product_id");
                    if (pid == null) pid = row.get("id");  // 兜底其他实体类型
                    String key = pid != null ? pid.toString() : null;
                    if (key == null || seen.add(key)) {
                        deduped.add(row);
                    }
                }
                if (deduped.size() < sorted.size()) {
                    log.info("[{}] Deduped by product_id: {} -> {}",
                        BLOCK_META.blockId(), sorted.size(), deduped.size());
                }
            }

            int limit = Math.min(topN, deduped.size());
            List<Map<String, Object>> outputData = new ArrayList<>(deduped.subList(0, limit));

            List<String> outputFields = context.getAvailableFields() != null
                ? new ArrayList<>(context.getAvailableFields()) : new ArrayList<>();

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, sorted by {} {}, top {} of {}, duration={}ms",
                BLOCK_META.blockId(), sortBy, order, outputData.size(), inputCount, durationMs);

            return BlockResult.success(outputData, outputFields,
                context.getCurrentOutputType(), inputCount, outputData.size(), durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[{}] Execution failed: {}", BLOCK_META.blockId(), e.getMessage(), e);
            return BlockResult.fail(e.getMessage(), inputCount, durationMs);
        }
    }

    private int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (ExecutorUtils.isNumeric(a) && ExecutorUtils.isNumeric(b)) {
            return Double.compare(ExecutorUtils.toDouble(a), ExecutorUtils.toDouble(b));
        }
        return a.toString().compareTo(b.toString());
    }
}
