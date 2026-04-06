package com.tiktok.selection.engine.datasource.executor;

import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.datasource.request.KeywordInsightRequest;
import com.tiktok.selection.manager.EchotikApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DS18 - 关键词洞察 (Keyword Insight).
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class KeywordInsightExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(KeywordInsightExecutor.class);
    private static final McpBlock BLOCK_META = KeywordInsightRequest.class.getAnnotation(McpBlock.class);

    private final EchotikApiClient echotikApiClient;

    public KeywordInsightExecutor(EchotikApiClient echotikApiClient) {
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
            KeywordInsightRequest req = KeywordInsightRequest.from(context.getBlockConfig());
            List<Map<String, Object>> data =
                echotikApiClient.requestList(BLOCK_META.endpoint(), req.toApiParams(),
                    context.getEchotikApiKey(), context.getEchotikApiSecret());

            if (data == null) {
                data = Collections.emptyList();
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Execution completed, outputCount={}, duration={}ms",
                BLOCK_META.blockId(), data.size(), durationMs);

            BlockResult result = BlockResult.success(data, new ArrayList<>(KeywordInsightRequest.OUTPUT_FIELDS),
                BLOCK_META.outputType(), inputCount, data.size(), durationMs);
            result.setEchotikApiCalls(1);
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
