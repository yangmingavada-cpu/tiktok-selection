package com.tiktok.selection.engine.enrichment.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.enrichment.request.InfluencerDetailEnrichmentRequest;
import com.tiktok.selection.manager.EchotikApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * EN03 - 达人详情补充 (Influencer Detail Enrichment).
 * 每批最多 10 个 user_id，调用 influencer/detail 获取详情后合并回原列表。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class InfluencerDetailEnrichmentExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(InfluencerDetailEnrichmentExecutor.class);
    private static final McpBlock BLOCK_META = InfluencerDetailEnrichmentRequest.class.getAnnotation(McpBlock.class);
    private static final String ID_FIELD = "influencer_id";
    private static final int BATCH_SIZE = 10;

    private final EchotikApiClient echotikApiClient;

    public InfluencerDetailEnrichmentExecutor(EchotikApiClient echotikApiClient) {
        this.echotikApiClient = echotikApiClient;
    }

    @Override
    public String getBlockId() {
        return BLOCK_META.blockId();
    }

    @Override
    public BlockResult execute(BlockContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("[{}] Execution started, sessionId={}", BLOCK_META.blockId(), context.getSessionId());

        List<Map<String, Object>> inputData = context.getInputData();
        int inputCount = inputData != null ? inputData.size() : 0;

        if (inputData == null || inputData.isEmpty()) {
            log.warn("[{}] Empty input, returning empty result", BLOCK_META.blockId());
            BlockResult result = BlockResult.success(new ArrayList<>(),
                new ArrayList<>(context.getAvailableFields()), BLOCK_META.outputType(), 0, 0, 0L);
            result.setEchotikApiCalls(0);
            result.setApiEndpointsCalled(List.of(BLOCK_META.endpoint()));
            return result;
        }

        List<Map<String, Object>> enriched = copyAsMutable(inputData);
        int apiCalls = 0;

        try {
            for (int i = 0; i < enriched.size(); i += BATCH_SIZE) {
                List<Map<String, Object>> batch = enriched.subList(i,
                    Math.min(i + BATCH_SIZE, enriched.size()));
                apiCalls += enrichBatch(batch, context);
            }

            List<String> outputFields = buildOutputFields(context.getAvailableFields());
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, count={}, apiCalls={}, duration={}ms",
                BLOCK_META.blockId(), enriched.size(), apiCalls, durationMs);

            BlockResult result = BlockResult.success(enriched, outputFields,
                BLOCK_META.outputType(), inputCount, enriched.size(), durationMs);
            result.setEchotikApiCalls(apiCalls);
            result.setApiEndpointsCalled(List.of(BLOCK_META.endpoint()));
            result.setEchotikKeyId(context.getEchotikKeyId());
            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[{}] Execution failed: {}", BLOCK_META.blockId(), e.getMessage(), e);
            BlockResult result = BlockResult.fail(e.getMessage(), inputCount, durationMs);
            result.setEchotikApiCalls(apiCalls);
            result.setApiEndpointsCalled(List.of(BLOCK_META.endpoint()));
            result.setEchotikKeyId(context.getEchotikKeyId());
            return result;
        }
    }

    private int enrichBatch(List<Map<String, Object>> batch, BlockContext context) {
        List<String> ids = batch.stream()
            .map(item -> Objects.toString(item.get(ID_FIELD), null))
            .filter(Objects::nonNull)
            .toList();
        if (ids.isEmpty()) {
            return 0;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("user_ids", String.join(",", ids)); // API 参数名为 user_ids

        List<Map<String, Object>> detailList =
            echotikApiClient.requestList(BLOCK_META.endpoint(), params,
                context.getEchotikApiKey(), context.getEchotikApiSecret());

        if (detailList != null && !detailList.isEmpty()) {
            Map<String, Map<String, Object>> detailMap = new HashMap<>();
            for (Map<String, Object> detail : detailList) {
                Object id = detail.get(ID_FIELD);
                if (id != null) detailMap.put(id.toString(), detail);
            }
            batch.forEach(item -> {
                Object itemId = item.get(ID_FIELD);
                if (itemId != null) {
                    Map<String, Object> detail = detailMap.get(itemId.toString());
                    if (detail != null) detail.forEach(item::putIfAbsent);
                }
            });
        }
        return 1;
    }

    private static List<Map<String, Object>> copyAsMutable(List<Map<String, Object>> src) {
        List<Map<String, Object>> copy = new ArrayList<>(src.size());
        for (Map<String, Object> item : src) copy.add(new LinkedHashMap<>(item));
        return copy;
    }

    private static List<String> buildOutputFields(List<String> availableFields) {
        List<String> fields = new ArrayList<>(availableFields != null ? availableFields : List.of());
        for (String extra : InfluencerDetailEnrichmentRequest.EXTRA_FIELDS) {
            if (!fields.contains(extra)) fields.add(extra);
        }
        return fields;
    }
}
