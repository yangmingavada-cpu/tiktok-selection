package com.tiktok.selection.engine.score.executor;

import com.tiktok.selection.common.BlockSecurityUtil;
import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.score.request.ScoreAggregateRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ScoreAggregateExecutor implements BlockExecutor {

    private static final McpBlock BLOCK_META = ScoreAggregateRequest.class.getAnnotation(McpBlock.class);

    @Override
    public String getBlockId() {
        return BLOCK_META.blockId();
    }

    @Override
    public BlockResult execute(BlockContext context) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> inputData = context.getInputData();
        ScoreAggregateRequest req = ScoreAggregateRequest.from(context.getBlockConfig());

        List<Map<String, Object>> dimensions = req.dimensions != null ? req.dimensions : List.of();
        String outputField = BlockSecurityUtil.validateOutputField(req.outputField, "total_score");
        int totalWeight = calcTotalWeight(dimensions);

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map<String, Object> item : inputData) {
            output.add(scoreRow(item, dimensions, outputField, totalWeight));
        }

        List<String> outputFields = new ArrayList<>(context.getAvailableFields());
        if (!outputFields.contains(outputField)) outputFields.add(outputField);

        return BlockResult.success(output, outputFields, context.getCurrentOutputType(),
            inputData.size(), output.size(), System.currentTimeMillis() - start);
    }

    private int calcTotalWeight(List<Map<String, Object>> dimensions) {
        int total = dimensions.stream()
            .mapToInt(d -> d.get("weight") instanceof Number n ? n.intValue() : 1)
            .sum();
        return total == 0 ? 1 : total;
    }

    private Map<String, Object> scoreRow(Map<String, Object> item,
                                         List<Map<String, Object>> dimensions,
                                         String outputField,
                                         int totalWeight) {
        Map<String, Object> row = new LinkedHashMap<>(item);
        double weightedSum = calcWeightedSum(item, dimensions);
        row.put(outputField, (long) (weightedSum / totalWeight));
        return row;
    }

    private double calcWeightedSum(Map<String, Object> item, List<Map<String, Object>> dimensions) {
        double sum = 0;
        for (Map<String, Object> dim : dimensions) {
            String scoreField = (String) dim.get("output_field");
            int weight = dim.get("weight") instanceof Number n ? n.intValue() : 1;
            Object scoreVal = scoreField != null ? item.get(scoreField) : null;
            if (scoreVal instanceof Number n) {
                sum += n.doubleValue() * weight;
            }
        }
        return sum;
    }
}
