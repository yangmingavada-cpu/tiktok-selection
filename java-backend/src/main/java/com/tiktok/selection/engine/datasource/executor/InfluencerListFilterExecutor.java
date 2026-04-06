package com.tiktok.selection.engine.datasource.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.datasource.request.InfluencerListFilterRequest;
import com.tiktok.selection.manager.EchotikApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DS03 - 达人列表筛选 (Influencer List Filter).
 * Queries the Echotik influencer/list endpoint with region, category,
 * follower/view/interaction range filters and sort options, supporting pagination.
 *
 * @author system
 * @date 2026/03/22
 */
@Component
public class InfluencerListFilterExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(InfluencerListFilterExecutor.class);
    private static final McpBlock BLOCK_META = InfluencerListFilterRequest.class.getAnnotation(McpBlock.class);

    private final EchotikApiClient echotikApiClient;

    public InfluencerListFilterExecutor(EchotikApiClient echotikApiClient) {
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

        Integer inputCount = context.getInputData() != null ? context.getInputData().size() : 0;

        try {
            InfluencerListFilterRequest req = InfluencerListFilterRequest.from(context.getBlockConfig());
            Map<String, Object> baseParams = req.toBaseApiParams();

            int pageSize = req.getEffectivePageSize();
            int totalPages = req.getEffectiveTotalPages();

            List<Map<String, Object>> allData = new ArrayList<>();
            int apiCalls = 0;
            int consecutiveFailures = 0;
            for (int page = 1; page <= totalPages; page++) {
                baseParams.put("page_num", String.valueOf(page));
                List<Map<String, Object>> pageData;
                try {
                    pageData = echotikApiClient.requestList(BLOCK_META.endpoint(), baseParams,
                        context.getEchotikApiKey(), context.getEchotikApiSecret());
                    apiCalls++;
                    consecutiveFailures = 0;
                } catch (Exception pageEx) {
                    apiCalls++;
                    consecutiveFailures++;
                    log.warn("[{}] Page {}/{} failed (consecutive={}): {}",
                        BLOCK_META.blockId(), page, totalPages, consecutiveFailures, pageEx.getMessage());
                    if (consecutiveFailures >= 3) {
                        log.warn("[{}] Stopping pagination after {} consecutive failures, keeping {} items",
                            BLOCK_META.blockId(), consecutiveFailures, allData.size());
                        break;
                    }
                    continue;
                }
                boolean lastPage = pageData == null || pageData.isEmpty() || pageData.size() < pageSize;
                if (pageData != null) {
                    allData.addAll(pageData);
                }
                log.debug("[{}] Page {}/{} fetched, items={}", BLOCK_META.blockId(), page, totalPages,
                    pageData != null ? pageData.size() : 0);
                if (lastPage) {
                    break;
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, outputCount={}, pages={}, duration={}ms",
                BLOCK_META.blockId(), allData.size(), apiCalls, durationMs);

            BlockResult result = BlockResult.success(allData, new ArrayList<>(InfluencerListFilterRequest.OUTPUT_FIELDS),
                BLOCK_META.outputType(), inputCount, allData.size(), durationMs);
            result.setEchotikApiCalls(apiCalls);
            result.setApiEndpointsCalled(List.of(BLOCK_META.endpoint()));
            result.setEchotikKeyId(context.getEchotikKeyId());
            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("[{}] Execution failed: {}", BLOCK_META.blockId(), e.getMessage(), e);
            BlockResult result = BlockResult.fail(e.getMessage(), inputCount, durationMs);
            result.setEchotikApiCalls(1);
            result.setApiEndpointsCalled(List.of(BLOCK_META.endpoint()));
            result.setEchotikKeyId(context.getEchotikKeyId());
            return result;
        }
    }
}
