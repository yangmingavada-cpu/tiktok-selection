package com.tiktok.selection.engine.traverse.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.traverse.request.SellerProductTraverseRequest;
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
 * TR09 - 店铺→商品 (Seller to Product Traverse).
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class SellerProductTraverseExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(SellerProductTraverseExecutor.class);
    private static final McpBlock BLOCK_META = SellerProductTraverseRequest.class.getAnnotation(McpBlock.class);
    private static final String INPUT_ID_FIELD = "seller_id";

    private final EchotikApiClient echotikApiClient;

    public SellerProductTraverseExecutor(EchotikApiClient echotikApiClient) {
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
                new ArrayList<>(SellerProductTraverseRequest.OUTPUT_FIELDS), BLOCK_META.outputType(), 0, 0, 0L);
            result.setEchotikApiCalls(0);
            result.setApiEndpointsCalled(List.of(BLOCK_META.endpoint()));
            return result;
        }

        SellerProductTraverseRequest req = SellerProductTraverseRequest.from(context.getBlockConfig());
        int maxItems = req.getEffectiveMaxItems();
        int pageSize = req.getEffectivePageSize();
        int maxProductsPerSeller = req.getEffectiveMaxProductsPerSeller();

        List<Map<String, Object>> allResults = new ArrayList<>();
        List<Map<String, Object>> itemsToProcess = inputData.subList(0, Math.min(maxItems, inputData.size()));
        int apiCalls = 0;

        try {
            for (Map<String, Object> item : itemsToProcess) {
                Object entityId = item.get(INPUT_ID_FIELD);
                if (entityId == null) {
                    continue;
                }

                int pageNum = 1;
                int fetchedCount = 0;

                while (fetchedCount < maxProductsPerSeller) {
                    Map<String, Object> params = new HashMap<>();
                    params.put("seller_id", entityId.toString());
                    params.put("page_num", pageNum);
                    params.put("page_size", String.valueOf(pageSize)); // API要求string类型
                    if (req.seller_product_sort_field != null) {
                        params.put("seller_product_sort_field", req.seller_product_sort_field);
                    }
                    if (req.sort_type != null) {
                        params.put("sort_type", req.sort_type);
                    }

                    List<Map<String, Object>> pageResults =
                        echotikApiClient.requestList(BLOCK_META.endpoint(), params,
                            context.getEchotikApiKey(), context.getEchotikApiSecret());
                    apiCalls++;

                    if (pageResults == null || pageResults.isEmpty()) {
                        break;
                    }

                    allResults.addAll(pageResults);
                    fetchedCount += pageResults.size();

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
                new ArrayList<>(SellerProductTraverseRequest.OUTPUT_FIELDS),
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
