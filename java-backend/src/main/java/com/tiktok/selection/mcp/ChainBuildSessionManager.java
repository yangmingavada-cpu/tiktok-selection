package com.tiktok.selection.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ChainBuildSession Redis存储管理器
 * Key: mcp:chain:{sessionId}，TTL=2小时
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class ChainBuildSessionManager {

    private static final Logger log = LoggerFactory.getLogger(ChainBuildSessionManager.class);
    private static final String KEY_PREFIX = "mcp:chain:";
    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ChainBuildSessionManager(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(ChainBuildSession session) {
        // 规约异常第3条：catch时区分异常类型，JsonProcessingException是此处唯一可能的受检异常
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(KEY_PREFIX + session.getSessionId(), json, TTL);
        } catch (JsonProcessingException e) {
            // 规约异常第12条：不直接抛出RuntimeException，使用有业务含义的自定义异常
            // S2139：不在此处重复打日志，cause已附加到BusinessException，由GlobalExceptionHandler统一记录
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话状态保存失败", e);
        }
    }

    public ChainBuildSession get(String sessionId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        if (json == null) return null;
        // 规约异常第3条：catch时区分异常类型，明确捕获JsonProcessingException
        try {
            return objectMapper.readValue(json, ChainBuildSession.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize ChainBuildSession: sessionId={}", sessionId, e);
            return null;
        }
    }

    public ChainBuildSession getOrCreate(String sessionId) {
        ChainBuildSession session = get(sessionId);
        if (session == null) {
            session = new ChainBuildSession();
            session.setSessionId(sessionId);
            save(session);
            // 规约日志第6条：debug级别输出必须判断开关
            if (log.isDebugEnabled()) {
                log.debug("Created new ChainBuildSession: {}", sessionId);
            }
        }
        return session;
    }

    public void delete(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }

    /**
     * 从已有Session上下文初始化构建会话（增量模式）
     */
    public ChainBuildSession initFromContext(String sessionId, Map<String, Object> context) {
        ChainBuildSession session = new ChainBuildSession();
        session.setSessionId(sessionId);
        session.setIncrementalMode(true);

        if (context != null) {
            List<Map<String, Object>> blockList = applyContext(session, context);
            if (session.getCurrentOutputType() == null && !blockList.isEmpty()) {
                inferStateFromBlocks(session, blockList);
            }
        }

        save(session);
        log.info("Initialized ChainBuildSession from context: {}", sessionId);
        return session;
    }

    /**
     * 将 context Map 中的字段写入 session，返回解析出的 blockList（可能为空）。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> applyContext(ChainBuildSession session, Map<String, Object> context) {
        List<Map<String, Object>> blockList = List.of();
        Object blocksObj = context.get("blockChain");
        if (blocksObj instanceof List<?> list) {
            blockList = (List<Map<String, Object>>) list;
            session.setBlocks(new ArrayList<>(blockList));
            session.setSeqCounter(blockList.size());
            session.setHasDataSource(!blockList.isEmpty());
        }
        Object outputType = context.get("currentOutputType");
        if (outputType instanceof String s) {
            session.setCurrentOutputType(s);
        }
        Object fields = context.get("availableFields");
        if (fields instanceof List<?>) {
            session.setAvailableFields(new ArrayList<>((List<String>) fields));
        }
        Object scoreFields = context.get("scoreFields");
        if (scoreFields instanceof List<?>) {
            session.setScoreFields(new ArrayList<>((List<String>) scoreFields));
        }
        return blockList;
    }

    /**
     * 从 blockChain 中的 DS 块推断 currentOutputType 和 availableFields。
     * 前端 sessionContext 只传 blockChain 时使用，避免 outputType=null 导致 NPE。
     */
    @SuppressWarnings("unchecked")
    private void inferStateFromBlocks(ChainBuildSession session, List<Map<String, Object>> blocks) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Map<String, Object> block = blocks.get(i);
            String blockId = (String) block.get("blockId");
            boolean isSourceBlock = blockId != null && FieldDictionary.SOURCE_TYPE_BLOCK_MAP.values()
                    .stream().anyMatch(arr -> arr[0].equals(blockId));
            if (isSourceBlock) {
                String outputType = resolveOutputType(blockId, block);
                if (outputType != null) {
                    session.setCurrentOutputType(outputType);
                    List<String> bidFields = FieldDictionary.getFieldsForBlockId(blockId);
                    session.setAvailableFields(bidFields.isEmpty()
                            ? new ArrayList<>(FieldDictionary.getFieldsForType(outputType))
                            : new ArrayList<>(bidFields));
                } else {
                    log.warn("inferStateFromBlocks: DS block {} has no type mapping", blockId);
                }
                return; // DS 块已处理，退出
            }
        }
        log.warn("inferStateFromBlocks: no DS block found, outputType remains null");
    }

    /**
     * 从单个 DS 块解析输出类型。
     * 优先读 config.source_type（新工具写入），回退按 blockId 反查。
     */
    @SuppressWarnings("unchecked")
    private static String resolveOutputType(String blockId, Map<String, Object> block) {
        Object cfg = block.get("config");
        if (cfg instanceof Map<?, ?> config) {
            Object st = ((Map<String, Object>) config).get("source_type");
            if (st instanceof String sourceType) {
                String[] mapping = FieldDictionary.SOURCE_TYPE_BLOCK_MAP.get(sourceType);
                if (mapping != null && mapping.length >= 2) {
                    return mapping[1];
                }
            }
        }
        for (String[] mapping : FieldDictionary.SOURCE_TYPE_BLOCK_MAP.values()) {
            if (mapping[0].equals(blockId)) {
                return mapping[1];
            }
        }
        return null;
    }
}
