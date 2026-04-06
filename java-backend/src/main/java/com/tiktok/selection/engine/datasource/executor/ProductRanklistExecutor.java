package com.tiktok.selection.engine.datasource.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.datasource.request.ProductRanklistRequest;
import com.tiktok.selection.manager.EchotikApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DS02 - 商品榜单 (Product Ranking).
 * Queries the Echotik product/ranklist endpoint with date, rank type,
 * rank field and optional category filters, supporting pagination.
 *
 * @author system
 * @date 2026/03/22
 */
@Component
public class ProductRanklistExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProductRanklistExecutor.class);
    private static final McpBlock BLOCK_META = ProductRanklistRequest.class.getAnnotation(McpBlock.class);

    private final EchotikApiClient echotikApiClient;

    public ProductRanklistExecutor(EchotikApiClient echotikApiClient) {
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
            ProductRanklistRequest req = ProductRanklistRequest.from(context.getBlockConfig());

            // 多日期模式：date_list 优先；否则退化为单日期
            List<String> datesToQuery = (req.date_list != null && !req.date_list.isEmpty())
                    ? req.date_list
                    : List.of(req.date);

            Map<String, Map<String, Object>> deduped = new LinkedHashMap<>();
            int totalApiCalls = 0;

            for (String date : datesToQuery) {
                Map<String, Object> baseParams = req.toBaseApiParams();
                baseParams.put("date", date);   // 覆盖为当前循环日期

                int pageSize   = req.getEffectivePageSize();
                int totalPages = req.getEffectiveTotalPages();
                int consecutiveFailures = 0;

                for (int page = 1; page <= totalPages; page++) {
                    baseParams.put("page_num", page);
                    List<Map<String, Object>> pageData;
                    try {
                        pageData = echotikApiClient.requestList(BLOCK_META.endpoint(), baseParams,
                                context.getEchotikApiKey(), context.getEchotikApiSecret());
                        totalApiCalls++;
                        consecutiveFailures = 0;
                    } catch (Exception pageEx) {
                        totalApiCalls++;
                        consecutiveFailures++;
                        log.warn("[{}] date={} page={}/{} failed (consecutive={}): {}",
                                BLOCK_META.blockId(), date, page, totalPages,
                                consecutiveFailures, pageEx.getMessage());
                        if (consecutiveFailures >= 3) break;
                        continue;
                    }

                    if (pageData == null || pageData.isEmpty()) break;

                    // 去重：同一 product_id 保留 total_sale_cnt 最大的一条
                    for (Map<String, Object> row : pageData) {
                        String pid = String.valueOf(row.get("product_id"));
                        if (!deduped.containsKey(pid)) {
                            deduped.put(pid, row);
                        } else {
                            long existing = toLong(deduped.get(pid).get("total_sale_cnt"));
                            long current  = toLong(row.get("total_sale_cnt"));
                            if (current > existing) deduped.put(pid, row);
                        }
                    }

                    log.debug("[{}] date={} page={}/{} fetched items={}",
                            BLOCK_META.blockId(), date, page, totalPages, pageData.size());
                    if (pageData.size() < pageSize) break;
                }
            }

            List<Map<String, Object>> allData = new ArrayList<>(deduped.values());
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, dates={}, outputCount={}, apiCalls={}, duration={}ms",
                    BLOCK_META.blockId(), datesToQuery.size(), allData.size(), totalApiCalls, durationMs);

            BlockResult result = BlockResult.success(allData, new ArrayList<>(ProductRanklistRequest.OUTPUT_FIELDS),
                    BLOCK_META.outputType(), inputCount, allData.size(), durationMs);
            result.setEchotikApiCalls(totalApiCalls);
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

    private static long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) { try { return Long.parseLong(s); } catch (NumberFormatException e) { /* non-numeric, return 0 */ } }
        return 0L;
    }
}
