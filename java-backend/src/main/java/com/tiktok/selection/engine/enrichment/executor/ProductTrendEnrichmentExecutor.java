package com.tiktok.selection.engine.enrichment.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.enrichment.request.ProductTrendEnrichmentRequest;
import com.tiktok.selection.manager.EchotikApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EN02 - 商品历史趋势补充 (Product Historical Trend Enrichment).
 * 对每个商品调用 product/trend，分页获取日期区间内的趋势数据，
 * 将所有记录合并为 trend_data 列表追加到商品记录中。
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class ProductTrendEnrichmentExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProductTrendEnrichmentExecutor.class);
    private static final McpBlock BLOCK_META = ProductTrendEnrichmentRequest.class.getAnnotation(McpBlock.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String ID_FIELD = "product_id";

    private final EchotikApiClient echotikApiClient;

    public ProductTrendEnrichmentExecutor(EchotikApiClient echotikApiClient) {
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

        ProductTrendEnrichmentRequest req = ProductTrendEnrichmentRequest.from(context.getBlockConfig());
        int pageSize = req.getEffectivePageSize();
        int maxRecords = req.getEffectiveMaxRecords();

        String endDate = req.end_date != null ? req.end_date : LocalDate.now().format(DATE_FMT);
        String startDate = req.start_date != null ? req.start_date
            : LocalDate.now().minusDays(req.getEffectiveTrendDays()).format(DATE_FMT);

        List<Map<String, Object>> enriched = copyAsMutable(inputData);
        int apiCalls = 0;

        try {
            for (Map<String, Object> item : enriched) {
                Object entityId = item.get(ID_FIELD);
                if (entityId == null) continue;

                List<Map<String, Object>> trendRecords = new ArrayList<>();
                int pageNum = 1;

                while (trendRecords.size() < maxRecords) {
                    Map<String, Object> params = new HashMap<>();
                    params.put(ID_FIELD, entityId.toString());
                    params.put("start_date", startDate);
                    params.put("end_date", endDate);
                    params.put("page_num", pageNum);
                    params.put("page_size", pageSize);

                    List<Map<String, Object>> pageResults =
                        echotikApiClient.requestList(BLOCK_META.endpoint(), params,
                            context.getEchotikApiKey(), context.getEchotikApiSecret());
                    apiCalls++;

                    if (pageResults == null || pageResults.isEmpty()) break;

                    trendRecords.addAll(pageResults);

                    if (pageResults.size() < pageSize) break; // 已是最后一页
                    pageNum++;
                }

                item.putIfAbsent("trend_data", trendRecords);
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

    private static List<Map<String, Object>> copyAsMutable(List<Map<String, Object>> src) {
        List<Map<String, Object>> copy = new ArrayList<>(src.size());
        for (Map<String, Object> item : src) copy.add(new LinkedHashMap<>(item));
        return copy;
    }

    private static List<String> buildOutputFields(List<String> availableFields) {
        List<String> fields = new ArrayList<>(availableFields != null ? availableFields : List.of());
        for (String extra : ProductTrendEnrichmentRequest.EXTRA_FIELDS) {
            if (!fields.contains(extra)) fields.add(extra);
        }
        return fields;
    }
}
