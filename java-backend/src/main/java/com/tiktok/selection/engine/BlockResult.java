package com.tiktok.selection.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result returned by a {@link BlockExecutor} after execution.
 * Contains output data, field metadata, timing, and API-usage
 * statistics.
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class BlockResult {

    /** Status constant for a successfully completed block */
    public static final String STATUS_COMPLETED = "completed";

    /** Status constant for a failed block */
    public static final String STATUS_FAILED = "failed";

    /**
     * Output data rows produced by the block
     */
    private List<Map<String, Object>> outputData;

    /**
     * Field names present in the output data
     */
    private List<String> outputFields;

    /**
     * Output type, e.g. "product_list", "influencer_list"
     */
    private String outputType;

    /**
     * Number of input rows received
     */
    private Integer inputCount;

    /**
     * Number of output rows produced
     */
    private Integer outputCount;

    /**
     * Number of Echotik API calls made during execution
     */
    private Integer echotikApiCalls;

    /**
     * Number of LLM tokens consumed during execution
     */
    private Integer llmTokensUsed;

    /**
     * Execution duration in milliseconds
     */
    private Long durationMs;

    /**
     * Execution status: "completed" or "failed"
     */
    private String status;

    /**
     * Error message when status is "failed"
     */
    private String errorMessage;

    /**
     * List of API endpoint paths called during execution
     */
    private List<String> apiEndpointsCalled;

    /**
     * Echotik API key ID used during execution
     */
    private String echotikKeyId;

    /**
     * Additional metadata produced by the block
     */
    private Map<String, Object> metadata;

    /**
     * Creates a successful result.
     *
     * @param outputData   output data rows
     * @param outputFields field names in the output
     * @param outputType   output type identifier
     * @param inputCount   number of input rows
     * @param outputCount  number of output rows
     * @param durationMs   execution time in milliseconds
     * @return a completed {@link BlockResult}
     */
    public static BlockResult success(List<Map<String, Object>> outputData,
                                      List<String> outputFields,
                                      String outputType,
                                      Integer inputCount,
                                      Integer outputCount,
                                      Long durationMs) {
        BlockResult result = new BlockResult();
        result.setOutputData(outputData);
        result.setOutputFields(outputFields);
        result.setOutputType(outputType);
        result.setInputCount(inputCount);
        result.setOutputCount(outputCount);
        result.setDurationMs(durationMs);
        result.setStatus(STATUS_COMPLETED);
        result.setEchotikApiCalls(0);
        result.setLlmTokensUsed(0);
        result.setApiEndpointsCalled(new ArrayList<>());
        result.setMetadata(new HashMap<>());
        return result;
    }

    /**
     * Creates a failed result.
     *
     * @param errorMessage description of the failure
     * @param inputCount   number of input rows received
     * @param durationMs   execution time in milliseconds
     * @return a failed {@link BlockResult}
     */
    public static BlockResult fail(String errorMessage,
                                   Integer inputCount,
                                   Long durationMs) {
        BlockResult result = new BlockResult();
        result.setOutputData(new ArrayList<>());
        result.setOutputFields(new ArrayList<>());
        result.setOutputType(null);
        result.setInputCount(inputCount);
        result.setOutputCount(0);
        result.setDurationMs(durationMs);
        result.setStatus(STATUS_FAILED);
        result.setErrorMessage(errorMessage);
        result.setEchotikApiCalls(0);
        result.setLlmTokensUsed(0);
        result.setApiEndpointsCalled(new ArrayList<>());
        result.setMetadata(new HashMap<>());
        return result;
    }
}
