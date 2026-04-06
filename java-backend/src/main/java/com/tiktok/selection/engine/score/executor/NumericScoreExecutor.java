package com.tiktok.selection.engine.score.executor;

import com.tiktok.selection.common.BlockSecurityUtil;
import com.tiktok.selection.engine.BlockContext;
import com.tiktok.selection.engine.BlockExecutor;
import com.tiktok.selection.engine.BlockResult;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.score.request.NumericScoreRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class NumericScoreExecutor implements BlockExecutor {

    private static final McpBlock BLOCK_META = NumericScoreRequest.class.getAnnotation(McpBlock.class);

    private static final double TIER_THRESHOLD_S = 4.5;
    private static final double TIER_THRESHOLD_A = 4.0;
    private static final double TIER_THRESHOLD_B = 3.0;
    private static final double TIER_THRESHOLD_C = 2.0;
    private static final double TIER_RATIO_S = 1.0;
    private static final double TIER_RATIO_A = 0.8;
    private static final double TIER_RATIO_B = 0.6;
    private static final double TIER_RATIO_C = 0.4;
    private static final double TIER_RATIO_D = 0.2;

    @Override
    public String getBlockId() {
        return BLOCK_META.blockId();
    }

    @Override
    public BlockResult execute(BlockContext context) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> inputData = context.getInputData();
        NumericScoreRequest req = NumericScoreRequest.from(context.getBlockConfig());

        String sourceField = req.sourceField;
        String outputField = BlockSecurityUtil.validateOutputField(req.outputField, "score");
        String algorithm = req.algorithm != null ? req.algorithm : "linear_map";
        int maxScore = BlockSecurityUtil.validateMaxScore(req.maxScore != null ? req.maxScore : 100);

        if (sourceField == null || inputData == null) {
            return BlockResult.fail("SC01: source_field 或 inputData 为空", 0, System.currentTimeMillis() - start);
        }

        double[] range = calcMinMax(inputData, sourceField);
        double minVal = range[0];
        double maxVal = range[1];

        List<Map<String, Object>> output = new ArrayList<>();
        for (Map<String, Object> item : inputData) {
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put(outputField, Math.round(calcScore(item.get(sourceField), algorithm, minVal, maxVal, maxScore)));
            output.add(row);
        }

        List<String> outputFields = new ArrayList<>(context.getAvailableFields());
        if (!outputFields.contains(outputField)) outputFields.add(outputField);

        return BlockResult.success(output, outputFields, context.getCurrentOutputType(),
            inputData.size(), output.size(), System.currentTimeMillis() - start);
    }

    private double[] calcMinMax(List<Map<String, Object>> inputData, String sourceField) {
        double minVal = Double.MAX_VALUE;
        double maxVal = -Double.MAX_VALUE;
        for (Map<String, Object> item : inputData) {
            Object val = item.get(sourceField);
            if (val instanceof Number n) {
                double d = n.doubleValue();
                if (d < minVal) minVal = d;
                if (d > maxVal) maxVal = d;
            }
        }
        if (minVal == Double.MAX_VALUE) {
            return new double[]{0, 1};
        }
        return new double[]{minVal, maxVal};
    }

    private double calcScore(Object val, String algorithm, double minVal, double maxVal, int maxScore) {
        if (!(val instanceof Number n)) {
            return 0;
        }
        double d = n.doubleValue();
        return switch (algorithm) {
            case "inverse_map" -> maxVal > minVal
                ? (maxVal - d) / (maxVal - minVal) * maxScore : maxScore / 2.0;
            case "tier_map" -> calcTierScore(d, maxScore);
            default -> maxVal > minVal
                ? (d - minVal) / (maxVal - minVal) * maxScore : maxScore / 2.0;
        };
    }

    private double calcTierScore(double val, int maxScore) {
        if (val >= TIER_THRESHOLD_S) return maxScore * TIER_RATIO_S;
        if (val >= TIER_THRESHOLD_A) return maxScore * TIER_RATIO_A;
        if (val >= TIER_THRESHOLD_B) return maxScore * TIER_RATIO_B;
        if (val >= TIER_THRESHOLD_C) return maxScore * TIER_RATIO_C;
        return maxScore * TIER_RATIO_D;
    }
}
