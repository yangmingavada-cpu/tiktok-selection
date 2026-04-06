package com.tiktok.selection.engine;

import lombok.Data;
import lombok.ToString;

import java.util.List;
import java.util.Map;

/**
 * Execution context passed to each {@link BlockExecutor}.
 * Carries session info, block configuration, input data,
 * and Echotik API credentials acquired by the orchestrator.
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class BlockContext {

    /**
     * Unique session identifier for the current chain execution
     */
    private String sessionId;

    /**
     * ID of the user who triggered the execution
     */
    private String userId;

    /**
     * Step sequence number within the chain
     */
    private Integer seq;

    /**
     * Block identifier, e.g. "SOURCE_PRODUCT_LIST"
     */
    private String blockId;

    /**
     * Block configuration read from block_chain definition
     */
    private Map<String, Object> blockConfig;

    /**
     * Data produced by the previous step
     */
    private List<Map<String, Object>> inputData;

    /**
     * Currently available field names in the data flow
     */
    private List<String> availableFields;

    /**
     * Current output type, e.g. "product_list", "influencer_list"
     */
    private String currentOutputType;

    /**
     * Acquired Echotik API key ID (set by orchestrator)
     */
    private String echotikKeyId;

    /**
     * Decrypted Echotik API key
     * 规约第2条：敏感数据禁止直接展示，@Data生成的toString()中排除，防止日志/异常堆栈泄露
     */
    @ToString.Exclude
    private String echotikApiKey;

    /**
     * Decrypted Echotik API secret
     * 规约第2条：同上，排除在toString()之外
     */
    @ToString.Exclude
    private String echotikApiSecret;
}
