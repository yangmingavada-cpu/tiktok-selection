package com.tiktok.selection.engine;

/**
 * Block executor interface for the block chain engine.
 * Each block in a selection chain implements this interface
 * to provide its specific data-source, computation, or
 * filtering logic.
 *
 * @author system
 * @date 2026/03/22
 */
public interface BlockExecutor {

    /**
     * Returns the unique block identifier, e.g. "SOURCE_PRODUCT_LIST".
     *
     * @return block id
     */
    String getBlockId();

    /**
     * Executes the block logic within the given context.
     *
     * @param context execution context carrying input data, config and credentials
     * @return result of the block execution
     * @throws Exception if an unrecoverable error occurs
     */
    BlockResult execute(BlockContext context) throws Exception;
}
