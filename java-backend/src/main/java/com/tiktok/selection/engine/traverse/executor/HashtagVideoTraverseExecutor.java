package com.tiktok.selection.engine.traverse.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.traverse.request.HashtagVideoTraverseRequest;
import com.tiktok.selection.manager.EchotikApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TR14 - 话题→视频 (Hashtag to Video Traverse).
 * 使用游标分页（offset/has_more）逐页获取话题下的视频列表。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class HashtagVideoTraverseExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(HashtagVideoTraverseExecutor.class);
    private static final McpBlock BLOCK_META = HashtagVideoTraverseRequest.class.getAnnotation(McpBlock.class);
    private static final String INPUT_ID_FIELD = "hashtag_id";

    private final EchotikApiClient echotikApiClient;

    public HashtagVideoTraverseExecutor(EchotikApiClient echotikApiClient) {
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
            BlockResult result = BlockResult.success(Collections.emptyList(),
                new ArrayList<>(HashtagVideoTraverseRequest.OUTPUT_FIELDS), BLOCK_META.outputType(), 0, 0, 0L);
            result.setEchotikApiCalls(0);
            result.setApiEndpointsCalled(List.of(BLOCK_META.endpoint()));
            return result;
        }

        HashtagVideoTraverseRequest req = HashtagVideoTraverseRequest.from(context.getBlockConfig());
        int maxItems = req.getEffectiveMaxItems();
        int maxVideos = req.getEffectiveMaxVideos();

        List<Map<String, Object>> allResults = new ArrayList<>();
        List<Map<String, Object>> itemsToProcess = inputData.subList(0, Math.min(maxItems, inputData.size()));
        int apiCalls = 0;

        try {
            for (Map<String, Object> item : itemsToProcess) {
                Object entityId = item.get(INPUT_ID_FIELD);
                if (entityId == null) {
                    continue;
                }

                String offset = null;
                int fetchedCount = 0;

                while (fetchedCount < maxVideos) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("hashtag_id", entityId.toString());
                    params.put("region", req.region);
                    params.put("count", req.count);
                    if (offset != null) {
                        params.put("offset", offset);
                    }

                    // 使用 request() 获取完整响应，以读取 has_more 和 cursor
                    Map<String, Object> response = echotikApiClient.request(BLOCK_META.endpoint(), params,
                        context.getEchotikApiKey(), context.getEchotikApiSecret());
                    apiCalls++;

                    List<Map<String, Object>> pageResults = extractDataList(response);
                    if (pageResults.isEmpty()) {
                        break;
                    }

                    allResults.addAll(pageResults);
                    fetchedCount += pageResults.size();

                    // 游标分页：has_more=1 时继续，否则停止
                    Object hasMore = response.get("has_more");
                    if (!isHasMore(hasMore)) {
                        break;
                    }
                    Object cursor = response.get("cursor");
                    if (cursor == null) {
                        break;
                    }
                    offset = cursor.toString();
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, inputCount={}, outputCount={}, apiCalls={}, duration={}ms",
                BLOCK_META.blockId(), inputCount, allResults.size(), apiCalls, durationMs);

            BlockResult result = BlockResult.success(allResults,
                new ArrayList<>(HashtagVideoTraverseRequest.OUTPUT_FIELDS),
                BLOCK_META.outputType(), inputCount, allResults.size(), durationMs);
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDataList(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return Collections.emptyList();
    }

    private boolean isHasMore(Object hasMore) {
        if (hasMore == null) return false;
        if (hasMore instanceof Number n) return n.intValue() == 1;
        return "1".equals(hasMore.toString());
    }
}
