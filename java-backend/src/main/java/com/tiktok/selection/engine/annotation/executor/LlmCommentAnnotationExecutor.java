package com.tiktok.selection.engine.annotation.executor;

import com.tiktok.selection.common.BlockSecurityUtil;
import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.request.LlmCommentAnnotationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Component
public class LlmCommentAnnotationExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(LlmCommentAnnotationExecutor.class);
    private static final McpBlock BLOCK_META = LlmCommentAnnotationRequest.class.getAnnotation(McpBlock.class);
    private static final int BATCH_SIZE = 20;
    private static final String LLM_CONFIG_KEY = "llm_config";
    private static final String AI_COMMENT_FIELD = "ai_comment";

    private final WebClient webClient;

    @Value("${python.ai.base-url:http://localhost:8000}")
    private String pythonAiBaseUrl;

    public LlmCommentAnnotationExecutor(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public String getBlockId() {
        return BLOCK_META.blockId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public BlockResult execute(BlockContext context) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> inputData = context.getInputData();
        LlmCommentAnnotationRequest req = LlmCommentAnnotationRequest.from(context.getBlockConfig());

        String language = BlockSecurityUtil.validateLanguage(req.language);
        int rawMaxChars = req.maxChars != null ? req.maxChars : 100;
        int maxChars = BlockSecurityUtil.validateMaxChars(rawMaxChars);
        Map<String, Object> llmConfig = context.getBlockConfig().get(LLM_CONFIG_KEY) instanceof Map<?, ?>
            ? (Map<String, Object>) context.getBlockConfig().get(LLM_CONFIG_KEY) : Map.of();

        BlockSecurityUtil.validateInputSize(inputData.size());

        List<Map<String, Object>> output = new ArrayList<>(inputData);
        int totalTokens = 0;

        for (int i = 0; i < inputData.size(); i += BATCH_SIZE) {
            totalTokens += processBatch(inputData, output, i, maxChars, language, llmConfig);
        }

        List<String> outputFields = new ArrayList<>(context.getAvailableFields());
        if (!outputFields.contains(AI_COMMENT_FIELD)) outputFields.add(AI_COMMENT_FIELD);

        BlockResult result = BlockResult.success(output, outputFields, context.getCurrentOutputType(),
            inputData.size(), output.size(), System.currentTimeMillis() - start);
        result.setLlmTokensUsed(totalTokens);
        return result;
    }

    @SuppressWarnings("unchecked")
    private int processBatch(List<Map<String, Object>> inputData, List<Map<String, Object>> output,
                             int batchStart, int maxChars, String language,
                             Map<String, Object> llmConfig) {
        List<Map<String, Object>> batch = inputData.subList(batchStart,
            Math.min(batchStart + BATCH_SIZE, inputData.size()));

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("products", batch);
        reqBody.put("config", Map.of(
            "max_chars", maxChars,
            "language", language,
            "persona", "cross_border_analyst"
        ));
        reqBody.put(LLM_CONFIG_KEY, llmConfig);

        try {
            Map<String, Object> resp = webClient.post()
                .uri(pythonAiBaseUrl + "/comment/generate")
                .bodyValue(reqBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(60))
                .block();

            applyCommentResults(resp, output, batchStart);
            return extractTokenCount(resp);
        } catch (Exception e) {
            log.error("LA01 comment generation failed, batch={}: {}", batchStart / BATCH_SIZE, e.getMessage(), e);
            for (int j = batchStart; j < Math.min(batchStart + BATCH_SIZE, output.size()); j++) {
                output.get(j).put(AI_COMMENT_FIELD, "");
            }
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private void applyCommentResults(Map<String, Object> resp, List<Map<String, Object>> output,
                                     int batchStart) {
        if (resp == null || !(resp.get("results") instanceof List<?> results)) return;
        for (int j = 0; j < results.size(); j++) {
            Map<String, Object> r = (Map<String, Object>) results.get(j);
            int idx = batchStart + j;
            if (idx < output.size()) {
                String comment = BlockSecurityUtil.sanitizeContent(
                    (String) r.getOrDefault("comment", ""),
                    BlockSecurityUtil.MAX_CONTENT_LENGTH);
                output.get(idx).put(AI_COMMENT_FIELD, comment);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int extractTokenCount(Map<String, Object> resp) {
        if (resp == null || !(resp.get("usage") instanceof Map<?, ?> usage)) return 0;
        Object tokObj = ((Map<Object, Object>) usage).get("total_tokens");
        return tokObj instanceof Number n ? n.intValue() : 0;
    }
}
