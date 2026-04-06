package com.tiktok.selection.engine;

import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that holds all available {@link BlockExecutor} instances.
 * At startup, all executors are collected via Spring DI and indexed
 * by their block ID for O(1) lookup.
 *
 * @author system
 * @date 2026/03/22
 */
@Component
public class BlockExecutorRegistry {

    private static final Logger log = LoggerFactory.getLogger(
        BlockExecutorRegistry.class);

    private final Map<String, BlockExecutor> executorMap;

    /**
     * Constructs the registry from all {@link BlockExecutor} beans
     * discovered by Spring.
     *
     * @param executors list of all block executor beans
     */
    @Autowired
    public BlockExecutorRegistry(List<BlockExecutor> executors) {
        this.executorMap = new HashMap<>(executors.size());
        for (BlockExecutor executor : executors) {
            String blockId = executor.getBlockId();
            executorMap.put(blockId, executor);
            log.info("Registered block executor: {} -> {}",
                blockId, executor.getClass().getSimpleName());
        }
        log.info("BlockExecutorRegistry initialized with {} executors",
            executorMap.size());
    }

    /**
     * Returns the executor for the given block ID.
     *
     * @param blockId block identifier, e.g. "SOURCE_PRODUCT_LIST"
     * @return the matching executor
     * @throws BusinessException if no executor is registered
     *                            for the given block ID
     */
    public BlockExecutor getExecutor(String blockId) {
        BlockExecutor executor = executorMap.get(blockId);
        if (executor == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                "No executor registered for blockId: " + blockId);
        }
        return executor;
    }
}
