package com.tiktok.selection.engine.traverse.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.traverse.request.VideoProductTraverseRequest;
import com.tiktok.selection.manager.EchotikApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TR13 - 视频→商品 (Video to Product Traverse).
 * 支持批量传入 video_ids（逗号分隔），分批分页查询视频挂载商品。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class VideoProductTraverseExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(VideoProductTraverseExecutor.class);
    private static final McpBlock BLOCK_META = VideoProductTraverseRequest.class.getAnnotation(McpBlock.class);
    private static final String INPUT_ID_FIELD = "video_id";

    private final EchotikApiClient echotikApiClient;

    public VideoProductTraverseExecutor(EchotikApiClient echotikApiClient) {
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
                new ArrayList<>(VideoProductTraverseRequest.OUTPUT_FIELDS), BLOCK_META.outputType(), 0, 0, 0L);
            result.setEchotikApiCalls(0);
            result.setApiEndpointsCalled(List.of(BLOCK_META.endpoint()));
            return result;
        }

        VideoProductTraverseRequest req = VideoProductTraverseRequest.from(context.getBlockConfig());
        int pageSize = req.getEffectivePageSize();
        int batchSize = req.getEffectiveBatchSize();
        int maxProducts = req.getEffectiveMaxProducts();

        // 收集所有有效的 video_id
        List<String> allVideoIds = inputData.stream()
            .map(item -> item.get(INPUT_ID_FIELD))
            .filter(id -> id != null)
            .map(Object::toString)
            .collect(Collectors.toList());

        List<Map<String, Object>> allResults = new ArrayList<>();
        int apiCalls = 0;

        try {
            // 按 batchSize 分批，每批合并为逗号分隔的 video_ids
            for (int i = 0; i < allVideoIds.size() && allResults.size() < maxProducts; i += batchSize) {
                List<String> batch = allVideoIds.subList(i, Math.min(i + batchSize, allVideoIds.size()));
                String videoIdsParam = String.join(",", batch);

                int pageNum = 1;

                while (allResults.size() < maxProducts) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("video_ids", videoIdsParam);
                    params.put("page_num", pageNum);
                    params.put("page_size", pageSize);

                    List<Map<String, Object>> pageResults =
                        echotikApiClient.requestList(BLOCK_META.endpoint(), params,
                            context.getEchotikApiKey(), context.getEchotikApiSecret());
                    apiCalls++;

                    if (pageResults == null || pageResults.isEmpty()) {
                        break;
                    }

                    allResults.addAll(pageResults);

                    if (pageResults.size() < pageSize) {
                        break; // 已是最后一页
                    }
                    pageNum++;
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, inputCount={}, outputCount={}, apiCalls={}, duration={}ms",
                BLOCK_META.blockId(), inputCount, allResults.size(), apiCalls, durationMs);

            BlockResult result = BlockResult.success(allResults,
                new ArrayList<>(VideoProductTraverseRequest.OUTPUT_FIELDS),
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
}
