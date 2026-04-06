package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tiktok.selection.dto.response.McpToolVO;
import com.tiktok.selection.entity.McpToolConfig;
import com.tiktok.selection.mapper.McpToolConfigMapper;
import com.tiktok.selection.mcp.tool.McpTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MCP工具配置服务
 *
 * <p>热路径（isToolEnabled）走内存缓存，避免每次工具调用都查DB/Redis。
 * 管理员写入时同步更新缓存，变更即时生效。
 *
 * @author system
 * @date 2026/04/01
 */
@Service
@RequiredArgsConstructor
public class McpToolConfigService {

    private final McpToolConfigMapper mapper;
    private final List<McpTool> registeredTools;

    /** 内存缓存：toolName → enabled。未配置的工具默认可用。 */
    private final ConcurrentHashMap<String, Boolean> enabledCache   = new ConcurrentHashMap<>();
    /** 内存缓存：toolName → sensitiveFlag。未配置的工具默认不敏感。 */
    private final ConcurrentHashMap<String, Boolean> sensitiveCache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;

    // ── 热路径 ─────────────────────────────────────────────────────────────────

    /**
     * 判断工具是否启用。未在DB中配置的工具视为启用。
     */
    public boolean isToolEnabled(String toolName) {
        ensureCacheLoaded();
        return enabledCache.getOrDefault(toolName, true);
    }

    /**
     * 判断工具是否为敏感工具（需要付费 Tier 才能使用）。
     * 未在DB中配置的工具默认不敏感。
     */
    public boolean isSensitive(String toolName) {
        ensureCacheLoaded();
        return sensitiveCache.getOrDefault(toolName, false);
    }

    // ── Admin 操作 ─────────────────────────────────────────────────────────────

    /**
     * 列出所有工具（已注册 + DB中的历史记录），按 tag → toolName 排序。
     */
    public List<McpToolVO> listForAdmin() {
        Map<String, McpToolConfig> dbMap = mapper.selectList(null).stream()
                .collect(Collectors.toMap(McpToolConfig::getToolName, c -> c));

        Map<String, McpTool> beanMap = registeredTools.stream()
                .collect(Collectors.toMap(McpTool::getToolName, t -> t));

        Set<String> allNames = new HashSet<>();
        allNames.addAll(dbMap.keySet());
        allNames.addAll(beanMap.keySet());

        return allNames.stream().map(name -> {
            McpTool tool = beanMap.get(name);
            McpToolConfig cfg = dbMap.get(name);
            return new McpToolVO(
                    name,
                    tool != null ? tool.getTag() : "unknown",
                    cfg == null || Boolean.TRUE.equals(cfg.getEnabled()),
                    cfg != null ? cfg.getBanReason() : null,
                    cfg != null ? cfg.getUpdatedBy() : null,
                    cfg != null ? cfg.getUpdatedAt() : null,
                    tool != null
            );
        }).sorted(Comparator.comparing(McpToolVO::tag).thenComparing(McpToolVO::toolName))
                .toList();
    }

    @Transactional
    public void ban(String toolName, String banReason, String operatorId) {
        upsert(toolName, false, banReason, operatorId);
        enabledCache.put(toolName, false);
    }

    @Transactional
    public void unban(String toolName, String operatorId) {
        upsert(toolName, true, null, operatorId);
        enabledCache.put(toolName, true);
    }

    /**
     * 批量禁用某标签下的所有已注册工具（如一键关停所有 echotik 工具）。
     */
    @Transactional
    public void banByTag(String tag, String banReason, String operatorId) {
        registeredTools.stream()
                .filter(t -> tag.equals(t.getTag()))
                .forEach(t -> ban(t.getToolName(), banReason, operatorId));
    }

    // ── 私有方法 ──────────────────────────────────────────────────────────────

    private synchronized void ensureCacheLoaded() {
        if (!cacheLoaded) {
            mapper.selectList(null).forEach(c -> {
                enabledCache.put(c.getToolName(), Boolean.TRUE.equals(c.getEnabled()));
                sensitiveCache.put(c.getToolName(), Boolean.TRUE.equals(c.getSensitiveFlag()));
            });
            cacheLoaded = true;
        }
    }

    private void upsert(String toolName, boolean enabled, String banReason, String operatorId) {
        McpToolConfig existing = mapper.selectOne(
                new LambdaQueryWrapper<McpToolConfig>()
                        .eq(McpToolConfig::getToolName, toolName));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            McpToolConfig c = new McpToolConfig();
            c.setToolName(toolName);
            c.setEnabled(enabled);
            c.setBanReason(banReason);
            c.setUpdatedBy(operatorId);
            c.setUpdatedAt(now);
            c.setCreatedAt(now);
            mapper.insert(c);
        } else {
            existing.setEnabled(enabled);
            existing.setBanReason(enabled ? null : banReason);
            existing.setUpdatedBy(operatorId);
            existing.setUpdatedAt(now);
            mapper.updateById(existing);
        }
    }
}
