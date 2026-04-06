package com.tiktok.selection.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.selection.entity.EchotikApiKey;
import com.tiktok.selection.mapper.EchotikApiKeyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Echotik API密钥池管理器，负责密钥分配与用量追踪
 * <p>
 * 作为Manager层组件，封装Redis缓存方案与DAO层交互
 *
 * @author system
 * @date 2026/03/22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EchotikKeyPoolManager extends ServiceImpl<EchotikApiKeyMapper, EchotikApiKey> {

    private static final String KEY_POOL_HASH = "echotik:key_pool";
    private static final String USAGE_BUFFER_KEY = "echotik:usage_buffer";

    /** keyId格式校验：仅允许字母、数字、连字符，防止Redis数据污染导致DB操作异常 */
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{1,64}$");

    /**
     * Lua脚本：原子性地选出剩余调用次数最多的Key并递减1。
     * 使用单个Lua脚本替代原先的"读取全量→本地选最优→递减"三步操作，
     * 消除多线程并发下的 TOCTOU（检查时间与使用时间不一致）竞态条件。
     */
    private static final String ACQUIRE_KEY_LUA_SCRIPT = """
            local entries = redis.call('HGETALL', KEYS[1])
            if #entries == 0 then return nil end
            local bestKey = nil
            local maxRemaining = 0
            for i = 1, #entries, 2 do
              local remaining = tonumber(entries[i+1])
              if remaining and remaining > maxRemaining then
                maxRemaining = remaining
                bestKey = entries[i]
              end
            end
            if bestKey == nil or maxRemaining <= 0 then return nil end
            redis.call('HINCRBY', KEYS[1], bestKey, -1)
            return bestKey
            """;

    /** 预编译的Lua脚本对象，类加载时初始化，避免重复创建 */
    private static final DefaultRedisScript<String> ACQUIRE_KEY_SCRIPT;

    static {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText(ACQUIRE_KEY_LUA_SCRIPT);
        script.setResultType(String.class);
        ACQUIRE_KEY_SCRIPT = script;
    }

    /** Redis Key池缓存TTL（秒），从配置文件读取，避免魔法数字 */
    @Value("${echotik.key-pool.cache-ttl:60}")
    private int keyPoolCacheTtl;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 将所有活跃Key加载到Redis Hash（remaining_calls）。
     * 启动时及定时调度时调用，保持与DB同步。
     */
    public void refreshKeyPool() {
        List<EchotikApiKey> activeKeys = list(new LambdaQueryWrapper<EchotikApiKey>()
                .eq(EchotikApiKey::getActive, true)
                .gt(EchotikApiKey::getRemainingCalls, 0));

        Map<String, String> pool = new HashMap<>();
        for (EchotikApiKey key : activeKeys) {
            pool.put(key.getId(), String.valueOf(key.getRemainingCalls()));
        }

        stringRedisTemplate.delete(KEY_POOL_HASH);
        if (!pool.isEmpty()) {
            stringRedisTemplate.opsForHash().putAll(KEY_POOL_HASH, pool);
            stringRedisTemplate.expire(KEY_POOL_HASH, keyPoolCacheTtl, TimeUnit.SECONDS);
        }
        log.info("Echotik key pool refreshed, {} active keys loaded", pool.size());
    }

    /**
     * 原子性地选出剩余调用次数最多的Key并递减1。
     * <p>
     * 通过Lua脚本保证"选Key+递减"的原子性，消除高并发下的TOCTOU竞态。
     * 若Redis池为空，触发一次从DB的刷新；刷新后仍无可用Key则返回empty。
     *
     * @return 可用的Key实体，或empty（无可用Key时）
     */
    public Optional<EchotikApiKey> acquireKey() {
        String bestKeyId = stringRedisTemplate.execute(ACQUIRE_KEY_SCRIPT, List.of(KEY_POOL_HASH));

        if (bestKeyId == null) {
            // 池为空，尝试从DB刷新一次后再试
            refreshKeyPool();
            bestKeyId = stringRedisTemplate.execute(ACQUIRE_KEY_SCRIPT, List.of(KEY_POOL_HASH));
        }

        if (bestKeyId == null) {
            log.warn("No available Echotik API keys");
            return Optional.empty();
        }

        EchotikApiKey key = getById(bestKeyId);
        if (key == null) {
            log.warn("Echotik key found in Redis pool but not in DB, pool may be stale: keyId={}", bestKeyId);
            return Optional.empty();
        }

        // 告警检查（递减后的估算值，实际DB用量由flushUsageBuffer异步同步）
        long estimatedRemaining = (long) key.getRemainingCalls() - 1;
        if (estimatedRemaining <= key.getAlertThreshold()) {
            log.warn("Echotik key [{}] remaining calls ({}) at or below alert threshold ({})",
                    key.getName(), estimatedRemaining, key.getAlertThreshold());
        }

        return Optional.of(key);
    }

    /**
     * 缓冲一条使用记录，定时批量刷新到DB。
     */
    public void recordUsage(String keyId, String sessionId, String userId, String endpoint, int calls) {
        String usageRecord = String.join("|", keyId, sessionId, userId, endpoint,
                String.valueOf(calls), String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.opsForList().rightPush(USAGE_BUFFER_KEY, usageRecord);
    }

    /**
     * 定时将缓冲的用量记录批量写入DB。
     */
    @Scheduled(fixedDelayString = "${echotik.key-pool.flush-interval:60}000")
    public void flushUsageBuffer() {
        Long size = stringRedisTemplate.opsForList().size(USAGE_BUFFER_KEY);
        if (size == null || size == 0) return;

        List<String> records = stringRedisTemplate.opsForList().range(USAGE_BUFFER_KEY, 0, size - 1);
        stringRedisTemplate.opsForList().trim(USAGE_BUFFER_KEY, size, -1);

        if (records == null || records.isEmpty()) return;

        // 按Key聚合递减量
        Map<String, Integer> decrements = new HashMap<>();
        for (String usageEntry : records) {
            String[] parts = usageEntry.split("\\|");
            if (parts.length >= 5) {
                String keyId = parts[0];
                // 校验keyId格式，防止Redis数据污染影响DB操作
                if (!KEY_ID_PATTERN.matcher(keyId).matches()) {
                    log.warn("Invalid keyId format in usage buffer, skipping record: {}",
                            keyId.substring(0, Math.min(20, keyId.length())));
                    continue;
                }
                int calls = Integer.parseInt(parts[4]);
                decrements.merge(keyId, calls, Integer::sum);
            }
        }

        // 更新DB中的remaining_calls
        for (Map.Entry<String, Integer> entry : decrements.entrySet()) {
            EchotikApiKey key = getById(entry.getKey());
            if (key != null) {
                key.setRemainingCalls(Math.max(0, key.getRemainingCalls() - entry.getValue()));
                key.setLastUsedTime(LocalDateTime.now());
                updateById(key);
            }
        }

        log.info("Flushed {} usage records, {} keys updated", records.size(), decrements.size());
    }

    /**
     * 启动时刷新Key池。
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            refreshKeyPool();
        } catch (Exception e) {
            // 记录完整堆栈以便排查（Redis未就绪时应用仍可启动，下次定时刷新会自动恢复）
            log.error("Failed to initialize Echotik key pool, will retry on next scheduled refresh", e);
        }
    }
}
