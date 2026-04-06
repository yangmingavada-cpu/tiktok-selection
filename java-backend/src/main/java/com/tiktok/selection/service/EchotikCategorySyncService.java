package com.tiktok.selection.service;

import com.tiktok.selection.entity.EchotikApiKey;
import com.tiktok.selection.entity.EchotikCategory;
import com.tiktok.selection.manager.EchotikApiClient;
import com.tiktok.selection.mapper.EchotikCategoryMapper;
import com.tiktok.selection.mcp.FieldDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Echotik 品类数据同步服务
 *
 * <p>从 Echotik API 分层拉取 L1→L2→L3 三级品类数据，
 * 按地区批量写入 echotik_category 表。
 *
 * @author system
 * @date 2026/03/24
 */
@Service
public class EchotikCategorySyncService {

    private static final Logger log = LoggerFactory.getLogger(EchotikCategorySyncService.class);

    private static final String ENDPOINT_L1 = "category/l1";
    private static final String ENDPOINT_L2 = "category/l2";
    private static final String ENDPOINT_L3 = "category/l3";
    private static final String LANG_ZH = "zh-CN";
    private static final String LANG_EN = "en-US";

    private final EchotikApiClient echotikApiClient;
    private final EchotikApiKeyService echotikApiKeyService;
    private final EchotikCategoryMapper categoryMapper;

    public EchotikCategorySyncService(EchotikApiClient echotikApiClient,
                                      EchotikApiKeyService echotikApiKeyService,
                                      EchotikCategoryMapper categoryMapper) {
        this.echotikApiClient = echotikApiClient;
        this.echotikApiKeyService = echotikApiKeyService;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 同步所有地区的品类数据（L1→L2→L3 分层拉取）
     */
    public Map<String, Object> syncAll() {
        Optional<EchotikApiKey> keyOpt = echotikApiKeyService.getAvailableKey();
        if (keyOpt.isEmpty()) {
            return Map.of("success", false, "message", "无可用 Echotik API 密钥");
        }
        EchotikApiKey key = keyOpt.get();
        String username = echotikApiKeyService.decryptApiKey(key);
        String password = echotikApiKeyService.decryptApiSecret(key);

        int totalInserted = 0;
        int apiCalls = 0;
        List<String> errors = new ArrayList<>();
        Map<String, Integer> regionCounts = new LinkedHashMap<>();

        for (Map<String, String> regionInfo : FieldDictionary.REGIONS) {
            String region = regionInfo.get("code");
            try {
                int[] result = syncRegion(region, username, password);
                int count = result[0];
                int calls = result[1];
                totalInserted += count;
                apiCalls += calls;
                regionCounts.put(region, count);
                log.info("Category sync OK: region={}, count={}, apiCalls={}", region, count, calls);
            } catch (Exception e) {
                log.warn("Category sync failed for region {}: {}", region, e.getMessage());
                errors.add(region + ": " + e.getMessage());
                apiCalls += 1;
            }
        }

        echotikApiKeyService.decrementRemainingCalls(key.getId(), apiCalls);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", errors.isEmpty());
        result.put("totalCategories", totalInserted);
        result.put("apiCalls", apiCalls);
        result.put("regions", regionCounts);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return result;
    }

    /**
     * 分层同步单个地区的品类：L1 → 用 L1 ID 拉 L2 → 用 L2 ID 拉 L3
     * 每层先用 zh-CN 拉取中文名，再用 en-US 拉取英文名合并。
     *
     * @return [insertedCount, apiCalls]
     */
    private int[] syncRegion(String region, String username, String password) {
        List<EchotikCategory> allCategories = new ArrayList<>();
        int apiCalls = 0;

        // ── L1：zh-CN + en-US ──
        List<Map<String, Object>> l1Zh = fetchSafe(ENDPOINT_L1, Map.of("language", LANG_ZH), username, password);
        List<Map<String, Object>> l1En = fetchSafe(ENDPOINT_L1, Map.of("language", LANG_EN), username, password);
        apiCalls += 2;

        Map<String, String> enNameMap = buildNameMap(l1En);
        for (Map<String, Object> item : l1Zh) {
            EchotikCategory cat = toEntity(item, region, 1, null);
            cat.setNameEn(enNameMap.get(cat.getCategoryId()));
            allCategories.add(cat);
        }
        log.debug("Region {} L1: {} categories", region, l1Zh.size());

        // ── L2 ──
        List<Map<String, Object>> l2Zh = fetchSafe(ENDPOINT_L2, Map.of("language", LANG_ZH), username, password);
        List<Map<String, Object>> l2En = fetchSafe(ENDPOINT_L2, Map.of("language", LANG_EN), username, password);
        apiCalls += 2;

        if (l2Zh.isEmpty() && !l1Zh.isEmpty()) {
            for (Map<String, Object> l1 : l1Zh) {
                String l1Id = extractString(l1, "category_id", "id");
                if (l1Id == null) continue;
                l2Zh.addAll(fetchSafe(ENDPOINT_L2, Map.of("language", LANG_ZH, "parent_id", l1Id), username, password));
                l2En.addAll(fetchSafe(ENDPOINT_L2, Map.of("language", LANG_EN, "parent_id", l1Id), username, password));
                apiCalls += 2;
            }
        }

        enNameMap = buildNameMap(l2En);
        for (Map<String, Object> item : l2Zh) {
            String parentId = extractString(item, "parent_id", "parent_category_id", "l1_category_id");
            EchotikCategory cat = toEntity(item, region, 2, parentId);
            cat.setNameEn(enNameMap.get(cat.getCategoryId()));
            allCategories.add(cat);
        }
        log.debug("Region {} L2: {} categories", region, l2Zh.size());

        // ── L3 ──
        List<Map<String, Object>> l3Zh = fetchSafe(ENDPOINT_L3, Map.of("language", LANG_ZH), username, password);
        List<Map<String, Object>> l3En = fetchSafe(ENDPOINT_L3, Map.of("language", LANG_EN), username, password);
        apiCalls += 2;

        if (l3Zh.isEmpty() && !l2Zh.isEmpty()) {
            for (Map<String, Object> l2 : l2Zh) {
                String l2Id = extractString(l2, "category_id", "id");
                if (l2Id == null) continue;
                l3Zh.addAll(fetchSafe(ENDPOINT_L3, Map.of("language", LANG_ZH, "parent_id", l2Id), username, password));
                l3En.addAll(fetchSafe(ENDPOINT_L3, Map.of("language", LANG_EN, "parent_id", l2Id), username, password));
                apiCalls += 2;
            }
        }

        enNameMap = buildNameMap(l3En);
        for (Map<String, Object> item : l3Zh) {
            String parentId = extractString(item, "parent_id", "parent_category_id", "l2_category_id");
            EchotikCategory cat = toEntity(item, region, 3, parentId);
            cat.setNameEn(enNameMap.get(cat.getCategoryId()));
            allCategories.add(cat);
        }
        log.debug("Region {} L3: {} categories", region, l3Zh.size());

        if (allCategories.isEmpty()) {
            return new int[]{0, apiCalls};
        }

        // 清除该地区旧数据后批量插入
        categoryMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EchotikCategory>()
                        .eq(EchotikCategory::getRegion, region));

        for (EchotikCategory cat : allCategories) {
            categoryMapper.insert(cat);
        }

        return new int[]{allCategories.size(), apiCalls};
    }

    private List<Map<String, Object>> fetchSafe(String endpoint, Map<String, Object> params,
                                                 String username, String password) {
        try {
            return echotikApiClient.requestList(endpoint, params, username, password);
        } catch (Exception e) {
            log.warn("Failed to fetch {}: {}", endpoint, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 将 en-US 响应列表转为 categoryId → englishName 映射
     */
    private Map<String, String> buildNameMap(List<Map<String, Object>> enData) {
        Map<String, String> map = HashMap.newHashMap(enData.size());
        for (Map<String, Object> item : enData) {
            String id = extractString(item, "category_id", "id");
            String name = extractString(item, "category_name", "name", "category_name_en");
            if (id != null && name != null) {
                map.put(id, name);
            }
        }
        return map;
    }

    /**
     * zh-CN 响应条目 → 实体（category_name 存入 nameZh，nameEn 由调用方通过 buildNameMap 设置）
     */
    private EchotikCategory toEntity(Map<String, Object> item, String region, int level, String parentId) {
        EchotikCategory cat = new EchotikCategory();
        cat.setCategoryId(extractString(item, "category_id", "id"));
        cat.setRegion(region);
        cat.setLevel(level);
        cat.setParentId(parentId);
        cat.setNameZh(extractString(item, "category_name", "name", "category_name_cn"));
        cat.setCreateTime(LocalDateTime.now());
        cat.setUpdateTime(LocalDateTime.now());
        return cat;
    }

    private String extractString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) {
                return val.toString();
            }
        }
        return null;
    }
}
