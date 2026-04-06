package com.tiktok.selection.engine.score.executor;

import com.tiktok.selection.common.BlockSecurityUtil;
import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.score.request.LlmSemanticScoreRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

@Component
public class LlmSemanticScoreExecutor implements BlockExecutor {

    private static final Logger log = LoggerFactory.getLogger(LlmSemanticScoreExecutor.class);
    private static final McpBlock BLOCK_META = LlmSemanticScoreRequest.class.getAnnotation(McpBlock.class);
    private static final int BATCH_SIZE = 5;
    private static final String LLM_CONFIG_KEY = "llm_config";

    private final WebClient webClient;

    @Value("${python.ai.base-url:http://localhost:8000}")
    private String pythonAiBaseUrl;

    public LlmSemanticScoreExecutor(WebClient.Builder webClientBuilder) {
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
        LlmSemanticScoreRequest req = LlmSemanticScoreRequest.from(context.getBlockConfig());

        String semanticPrompt = BlockSecurityUtil.validatePrompt(
            req.semanticPrompt, "综合评估商品的TikTok带货潜力");
        String outputField = BlockSecurityUtil.validateOutputField(req.outputField, "semantic_score");
        int rawMaxScore = req.maxScore != null ? req.maxScore : 100;
        int maxScore = BlockSecurityUtil.validateMaxScore(rawMaxScore);
        Map<String, Object> llmConfig = context.getBlockConfig().get(LLM_CONFIG_KEY) instanceof Map<?, ?>
            ? (Map<String, Object>) context.getBlockConfig().get(LLM_CONFIG_KEY) : Map.of();

        BlockSecurityUtil.validateInputSize(inputData.size());

        List<Map<String, Object>> output = new ArrayList<>(inputData);
        int totalTokens = 0;

        for (int i = 0; i < inputData.size(); i += BATCH_SIZE) {
            totalTokens += processBatch(inputData, output, i, semanticPrompt, maxScore, llmConfig, outputField);
        }

        List<String> outputFields = new ArrayList<>(context.getAvailableFields());
        if (!outputFields.contains(outputField)) outputFields.add(outputField);

        BlockResult result = BlockResult.success(output, outputFields, context.getCurrentOutputType(),
            inputData.size(), output.size(), System.currentTimeMillis() - start);
        result.setLlmTokensUsed(totalTokens);
        return result;
    }

    @SuppressWarnings("unchecked")
    private int processBatch(List<Map<String, Object>> inputData, List<Map<String, Object>> output,
                             int batchStart, String semanticPrompt, int maxScore,
                             Map<String, Object> llmConfig, String outputField) {
        List<Map<String, Object>> batch = inputData.subList(batchStart,
            Math.min(batchStart + BATCH_SIZE, inputData.size()));

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("products", batch);
        reqBody.put("eval_prompt", semanticPrompt);
        reqBody.put("max_score", maxScore);
        reqBody.put(LLM_CONFIG_KEY, llmConfig);

        try {
            Map<String, Object> resp = webClient.post()
                .uri(pythonAiBaseUrl + "/score/evaluate")
                .bodyValue(reqBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(60))
                .block();

            applyScoreResults(resp, output, batchStart, outputField);
            return extractTokenCount(resp);
        } catch (Exception e) {
            log.error("SC02 batch scoring failed, batch={}: {}", batchStart / BATCH_SIZE, e.getMessage(), e);
            for (int j = batchStart; j < Math.min(batchStart + BATCH_SIZE, output.size()); j++) {
                output.get(j).put(outputField, 0);
            }
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private void applyScoreResults(Map<String, Object> resp, List<Map<String, Object>> output,
                                   int batchStart, String outputField) {
        if (resp == null || !(resp.get("results") instanceof List<?> results)) return;
        for (int j = 0; j < results.size(); j++) {
            Map<String, Object> r = (Map<String, Object>) results.get(j);
            int idx = batchStart + j;
            if (idx < output.size()) {
                output.get(idx).put(outputField, r.getOrDefault("score", 0));
                String reason = BlockSecurityUtil.sanitizeContent(
                    (String) r.getOrDefault("reason", ""),
                    BlockSecurityUtil.MAX_CONTENT_LENGTH);
                output.get(idx).put(outputField + "_reason", reason);
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
