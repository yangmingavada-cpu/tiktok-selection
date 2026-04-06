package com.tiktok.selection.engine.enrichment.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.enrichment.request.ProductCommentEnrichmentRequest;
import com.tiktok.selection.manager.EchotikApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EN05 - 商品评论补充 (Product Comment Enrichment).
 * 对每个商品调用 echotik/product/comment，分页获取评论列表，
 * 将所有评论合并为 comments 列表追加到商品记录中。
 *
 * @author system
 * @date 2026/03/26
 */
@Component
public class ProductCommentEnrichmentExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProductCommentEnrichmentExecutor.class);
    private static final McpBlock BLOCK_META = ProductCommentEnrichmentRequest.class.getAnnotation(McpBlock.class);
    private static final String ID_FIELD = "product_id";

    private final EchotikApiClient echotikApiClient;

    public ProductCommentEnrichmentExecutor(EchotikApiClient echotikApiClient) {
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

        ProductCommentEnrichmentRequest req = ProductCommentEnrichmentRequest.from(context.getBlockConfig());
        List<Map<String, Object>> enriched = copyAsMutable(inputData);

        FetchStats stats = enrichAllProducts(enriched, req, context);

        List<String> outputFields = buildOutputFields(context.getAvailableFields());
        long durationMs = System.currentTimeMillis() - startTime;
        log.info("[{}] Execution completed, count={}, apiCalls={}, failedProducts={}, duration={}ms",
            BLOCK_META.blockId(), enriched.size(), stats.apiCalls, stats.failedProducts, durationMs);

        BlockResult result = BlockResult.success(enriched, outputFields,
            BLOCK_META.outputType(), inputCount, enriched.size(), durationMs);
        result.setEchotikApiCalls(stats.apiCalls);
        result.setApiEndpointsCalled(List.of(BLOCK_META.endpoint()));
        result.setEchotikKeyId(context.getEchotikKeyId());
        return result;
    }

    /**
     * 遍历所有商品，逐个拉取评论并追加到记录中。单商品失败不影响其余商品。
     */
    private FetchStats enrichAllProducts(List<Map<String, Object>> items,
                                         ProductCommentEnrichmentRequest req,
                                         BlockContext context) {
        int apiCalls = 0;
        int failedProducts = 0;

        for (Map<String, Object> item : items) {
            Object entityId = item.get(ID_FIELD);
            if (entityId == null) continue;

            try {
                int calls = fetchAndAttachComments(item, entityId.toString(), req, context);
                apiCalls += calls;
            } catch (Exception ex) {
                apiCalls++;
                failedProducts++;
                log.warn("[{}] Comment fetch failed for product {}: {}",
                    BLOCK_META.blockId(), entityId, ex.getMessage());
                item.putIfAbsent("comments", List.of());
            }
        }
        return new FetchStats(apiCalls, failedProducts);
    }

    /**
     * 分页拉取单个商品的评论，追加到 item 的 comments 字段。
     *
     * @return 本次消耗的 API 调用次数
     */
    private int fetchAndAttachComments(Map<String, Object> item, String entityId,
                                       ProductCommentEnrichmentRequest req,
                                       BlockContext context) {
        int pageSize = req.getEffectivePageSize();
        int maxComments = req.getEffectiveMaxComments();
        List<Map<String, Object>> comments = new ArrayList<>();
        int apiCalls = 0;
        int pageNum = 1;

        while (comments.size() < maxComments) {
            Map<String, Object> params = new HashMap<>();
            params.put(ID_FIELD, entityId);
            params.put("page_num", pageNum);
            params.put("page_size", pageSize);

            List<Map<String, Object>> pageResults =
                echotikApiClient.requestList(BLOCK_META.endpoint(), params,
                    context.getEchotikApiKey(), context.getEchotikApiSecret());
            apiCalls++;

            if (pageResults == null || pageResults.isEmpty()) break;
            comments.addAll(pageResults);
            if (pageResults.size() < pageSize) break;
            pageNum++;
        }

        item.putIfAbsent("comments", comments);
        return apiCalls;
    }

    private static List<Map<String, Object>> copyAsMutable(List<Map<String, Object>> src) {
        List<Map<String, Object>> copy = new ArrayList<>(src.size());
        for (Map<String, Object> item : src) copy.add(new LinkedHashMap<>(item));
        return copy;
    }

    private static List<String> buildOutputFields(List<String> availableFields) {
        List<String> fields = new ArrayList<>(availableFields != null ? availableFields : List.of());
        for (String extra : ProductCommentEnrichmentRequest.EXTRA_FIELDS) {
            if (!fields.contains(extra)) fields.add(extra);
        }
        return fields;
    }

    private record FetchStats(int apiCalls, int failedProducts) {}
}
