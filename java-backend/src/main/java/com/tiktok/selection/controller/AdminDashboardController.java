package com.tiktok.selection.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tiktok.selection.common.R;
import com.tiktok.selection.entity.EchotikApiKey;
import com.tiktok.selection.entity.Session;
import com.tiktok.selection.entity.User;
import com.tiktok.selection.mapper.IntentParseLogMapper;
import com.tiktok.selection.service.EchotikApiKeyService;
import com.tiktok.selection.service.EchotikCategorySyncService;
import com.tiktok.selection.service.SessionService;
import com.tiktok.selection.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员运营看板接口
 *
 * <ul>
 *   <li>GET /api/admin/dashboard/overview — 总览统计（用户数/会话数/API调用/Token消耗）
 *   <li>GET /api/admin/dashboard/api-keys — API密钥余量概况
 * </ul>
 *
 * @author system
 * @date 2026/03/24
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    /** 用户/密钥 "启用" 状态值 */
    private static final String STATUS_ACTIVE = "active";
    /** 会话 "运行中" 状态值 */
    private static final String STATUS_RUNNING = "running";

    private final UserService userService;
    private final SessionService sessionService;
    private final EchotikApiKeyService echotikApiKeyService;
    private final EchotikCategorySyncService categorySyncService;
    private final IntentParseLogMapper intentParseLogMapper;

    /**
     * 运营总览统计
     *
     * <p>返回数据：
     * <ul>
     *   <li>totalUsers        — 总用户数
     *   <li>activeUsers       — 状态为 active 的用户数
     *   <li>todayNewUsers     — 今日新增用户数
     *   <li>totalSessions     — 总会话数（逻辑删除除外）
     *   <li>todaySessions     — 今日新建会话数
     *   <li>runningSessions   — 当前 running 状态会话数
     *   <li>totalEchotikCalls — 所有会话累计 Echotik API 调用次数
     *   <li>totalLlmTokens    — 所有会话累计 LLM Token 消耗
     *   <li>apiKeyPoolStatus  — 密钥池概况（总数/可用数/低余量预警数）
     * </ul>
     */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 用户统计
        long totalUsers = userService.count();
        long activeUsers = userService.count(
                new LambdaQueryWrapper<User>().eq(User::getStatus, STATUS_ACTIVE));
        long todayNewUsers = userService.count(
                new LambdaQueryWrapper<User>().ge(User::getCreateTime, todayStart));

        // 会话统计
        long totalSessions = sessionService.count();
        long todaySessions = sessionService.count(
                new LambdaQueryWrapper<Session>().ge(Session::getCreateTime, todayStart));
        long runningSessions = sessionService.count(
                new LambdaQueryWrapper<Session>().eq(Session::getStatus, STATUS_RUNNING));

        // 资源消耗统计（执行阶段 + 规划阶段合计）
        List<Session> allSessions = sessionService.list(
                new LambdaQueryWrapper<Session>()
                        .select(Session::getEchotikApiCalls, Session::getLlmTotalTokens));
        long execEchotikCalls = allSessions.stream()
                .mapToLong(s -> s.getEchotikApiCalls() != null ? s.getEchotikApiCalls() : 0L)
                .sum();
        long execTokens    = allSessions.stream()
                .mapToLong(s -> s.getLlmTotalTokens() != null ? s.getLlmTotalTokens() : 0L)
                .sum();
        long planningTokens   = intentParseLogMapper.sumTotalTokens();
        long planningApiCalls = intentParseLogMapper.sumTotalApiCalls();
        long totalLlmTokens   = execTokens + planningTokens;
        // 总 API 调用 = 执行阶段 Echotik 调用 + 规划阶段 LLM 调用
        long totalEchotikCalls = execEchotikCalls + planningApiCalls;

        // 密钥池概况
        long totalApiKeys = echotikApiKeyService.count();
        long activeApiKeys = echotikApiKeyService.count(
                new LambdaQueryWrapper<EchotikApiKey>().eq(EchotikApiKey::getActive, true));
        long lowQuotaKeys = echotikApiKeyService
                .list(new LambdaQueryWrapper<EchotikApiKey>().eq(EchotikApiKey::getActive, true))
                .stream()
                .filter(k -> k.getAlertThreshold() != null
                        && k.getRemainingCalls() != null
                        && k.getRemainingCalls() <= k.getAlertThreshold())
                .count();

        Map<String, Object> apiKeyPoolStatus = HashMap.newHashMap(4);
        apiKeyPoolStatus.put("total", totalApiKeys);
        apiKeyPoolStatus.put(STATUS_ACTIVE, activeApiKeys);
        apiKeyPoolStatus.put("lowQuotaCount", lowQuotaKeys);

        Map<String, Object> result = HashMap.newHashMap(10);
        result.put("totalUsers", totalUsers);
        result.put("activeUsers", activeUsers);
        result.put("todayNewUsers", todayNewUsers);
        result.put("totalSessions", totalSessions);
        result.put("todaySessions", todaySessions);
        result.put("runningSessions", runningSessions);
        result.put("totalEchotikCalls", totalEchotikCalls);
        result.put("totalLlmTokens", totalLlmTokens);
        result.put("planningTokens", planningTokens);
        result.put("planningApiCalls", planningApiCalls);
        result.put("execTokens", execTokens);
        result.put("execEchotikCalls", execEchotikCalls);
        result.put("apiKeyPoolStatus", apiKeyPoolStatus);

        return R.ok(result);
    }

    /**
     * 一键同步 Echotik 品类数据（L1/L2/L3，所有地区）
     * 从 Echotik API 拉取品类列表存入 echotik_category 表
     */
    @PostMapping("/sync-categories")
    public R<Map<String, Object>> syncCategories() {
        return R.ok(categorySyncService.syncAll());
    }

    /**
     * API 密钥余量概况列表（仅展示关键指标，不含密文）
     */
    @GetMapping("/api-keys")
    public R<List<Map<String, Object>>> apiKeyStats() {
        List<EchotikApiKey> keys = echotikApiKeyService.list(
                new LambdaQueryWrapper<EchotikApiKey>()
                        .select(EchotikApiKey::getId,
                                EchotikApiKey::getName,
                                EchotikApiKey::getTotalCalls,
                                EchotikApiKey::getRemainingCalls,
                                EchotikApiKey::getAlertThreshold,
                                EchotikApiKey::getActive,
                                EchotikApiKey::getLastUsedTime)
                        .orderByDesc(EchotikApiKey::getRemainingCalls));

        List<Map<String, Object>> statList = keys.stream().map(k -> {
            boolean belowThreshold = k.getAlertThreshold() != null
                    && k.getRemainingCalls() != null
                    && k.getRemainingCalls() <= k.getAlertThreshold();
            Map<String, Object> stat = HashMap.newHashMap(8);
            stat.put("id", k.getId());
            stat.put("name", k.getName());
            stat.put("totalCalls", k.getTotalCalls());
            stat.put("remainingCalls", k.getRemainingCalls());
            stat.put("alertThreshold", k.getAlertThreshold());
            stat.put("belowThreshold", belowThreshold);
            stat.put(STATUS_ACTIVE, k.getActive());
            stat.put("lastUsedTime", k.getLastUsedTime());
            return stat;
        }).toList();

        return R.ok(statList);
    }
}
