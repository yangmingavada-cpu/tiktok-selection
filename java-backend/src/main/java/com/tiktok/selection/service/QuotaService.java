package com.tiktok.selection.service;

import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.entity.User;
import com.tiktok.selection.entity.UserTier;
import com.tiktok.selection.mapper.UserTierMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 配额服务，负责检查和记录用户API调用与Token使用量
 *
 * @author system
 * @date 2026/03/22
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    /**
     * 规约第11条：LLM Block防刷——每用户每小时最多允许执行的LLM Block次数
     * 包含 SC02（语义评分）和 LA01（AI评语）
     */
    private static final int MAX_LLM_BLOCKS_PER_HOUR = 50;

    /** 滑动窗口时长：1小时（毫秒） */
    private static final long RATE_WINDOW_MS = 3_600_000L;

    /**
     * 每用户LLM Block调用时间戳队列（滑动窗口），内存级，重启后清零。
     * 并发安全：通过 {@link #userLocks} 中同一 userId 的锁对象保护，
     * 独立锁对象比 synchronized(deque) 语义更明确，避免锁目标混淆。
     */
    private final ConcurrentHashMap<String, ArrayDeque<Long>> llmCallTimestamps = new ConcurrentHashMap<>();

    /** 每用户独立锁，用于保护对应 deque 的复合读写操作 */
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final UserService userService;
    private final UserTierMapper userTierMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public QuotaService(UserService userService, UserTierMapper userTierMapper,
                        StringRedisTemplate stringRedisTemplate) {
        this.userService          = userService;
        this.userTierMapper       = userTierMapper;
        this.stringRedisTemplate  = stringRedisTemplate;
    }

    /**
     * 检查并计入月度 API 配额。
     *
     * <p>使用 Redis INCR 原子累加，首次写入时设置 31 天 TTL（自然月滚动）。
     * 超出 {@link UserTier#getMonthlyApiQuota()} 上限时抛出 {@link BusinessException}。
     *
     * @param userId            用户ID（null 时跳过，适用于内部调用）
     * @param estimatedApiCalls 本次调用计入的次数（通常为 1）
     * @throws BusinessException 月度配额已耗尽时抛出 A0605
     */
    public void checkQuota(String userId, Integer estimatedApiCalls) {
        if (userId == null) {
            return;
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_ACCOUNT_NOT_EXIST, "用户不存在: " + userId);
        }
        if (user.getTierId() == null) {
            log.warn("User has no tier assigned, skipping quota check: userId={}", userId);
            return;
        }

        UserTier tier = userTierMapper.selectById(user.getTierId());
        if (tier == null) {
            log.warn("UserTier not found for tierId={}, skipping quota check", user.getTierId());
            return;
        }

        Integer monthlyQuota = tier.getMonthlyApiQuota();
        if (monthlyQuota == null || monthlyQuota <= 0) {
            return;
        }

        // Redis INCR 原子累加月度计数
        int increment = (estimatedApiCalls != null && estimatedApiCalls > 0) ? estimatedApiCalls : 1;
        String key = "quota:monthly:" + userId + ":" + YearMonth.now().format(MONTH_FMT);
        Long current = stringRedisTemplate.opsForValue().increment(key, increment);

        // 首次写入（计数 == increment）时设置 31 天 TTL，保证下个月自动清零
        if (current != null && current == increment) {
            stringRedisTemplate.expire(key, 31, TimeUnit.DAYS);
        }

        if (current != null && current > monthlyQuota) {
            log.warn("Monthly quota exceeded: userId={} current={} limit={}", userId, current, monthlyQuota);
            throw new BusinessException(ErrorCode.QUOTA_EXHAUSTED,
                    "月度 API 配额已用尽（已用：" + current + "，上限：" + monthlyQuota + "），请下月再试或升级套餐");
        }

        log.debug("Quota check passed: userId={} tier={} used={}/{}", userId, tier.getName(), current, monthlyQuota);
    }

    /**
     * LLM Block防刷检查（规约第11条：必须实现防刷机制）
     * 滑动窗口算法：统计用户1小时内的LLM Block执行次数，超限则拒绝。
     * 适用于 SC02（语义评分）、LA01（AI评语）等调用外部LLM的Block。
     *
     * <p>内存级实现，重启后计数清零；并发安全由 synchronized(deque) 保证。
     *
     * @param userId 用户ID
     * @throws BusinessException 触发限流时抛出 A0501
     */
    public void checkLlmRateLimit(String userId) {
        if (userId == null) {
            // MCP internal call from Python agent — no authenticated user, skip rate limiting
            return;
        }
        long now = System.currentTimeMillis();
        long windowStart = now - RATE_WINDOW_MS;

        Object lock = userLocks.computeIfAbsent(userId, k -> new Object());
        ArrayDeque<Long> deque = llmCallTimestamps.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (lock) {
            // 移除滑动窗口之外的历史记录
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= MAX_LLM_BLOCKS_PER_HOUR) {
                log.warn("LLM rate limit exceeded: userId={}, count={}/hour", userId, deque.size());
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED,
                        "AI分析请求过于频繁，每小时最多执行" + MAX_LLM_BLOCKS_PER_HOUR + "次，请稍后重试");
            }
            deque.addLast(now);
        }
    }

    /**
     * 记录使用量（当前仅日志记录，完整实现在后续weeks）
     *
     * @param userId     用户ID
     * @param apiCalls   API调用次数
     * @param tokensUsed Token使用量
     */
    public void recordUsage(String userId, Integer apiCalls, Long tokensUsed) {
        log.info("Usage recorded: userId={}, apiCalls={}, tokensUsed={}",
                userId, apiCalls, tokensUsed);
    }
}
