package com.tiktok.selection.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.entity.EchotikCategory;
import com.tiktok.selection.mapper.EchotikCategoryMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * MCP资源提供者（按需只读知识源）
 * 支持的URI模板:
 * - echotik://regions
 * - echotik://categories/{region}
 * - echotik://fields/{entity_type}
 * - echotik://endpoints
 * - echotik://connection-rules
 * - echotik://scoring-algorithms
 * - chain://state
 * - chain://cost-estimate
 *
 * @author system
 * @date 2026/03/24
 */
@Component
public class McpResourceProvider {

    /** URI 路径参数白名单格式：字母/数字/下划线/连字符，防止路径穿越 */
    private static final Pattern SAFE_URI_PARAM = Pattern.compile("^[a-zA-Z0-9_\\-]{1,64}$");

    private final EchotikCategoryMapper categoryMapper;
    private final ChainBuildSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public McpResourceProvider(EchotikCategoryMapper categoryMapper,
                                ChainBuildSessionManager sessionManager,
                                ObjectMapper objectMapper) {
        this.categoryMapper = categoryMapper;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> read(String uri, String sessionId) {
        if (uri == null) {
            return errorContents(uri, "URI不能为空");
        }

        try {
            String content = resolveUri(uri, sessionId);
            return Map.of("contents", List.of(Map.of("uri", uri, "text", content)));
        } catch (Exception e) {
            return errorContents(uri, "读取资源失败: " + e.getMessage());
        }
    }

    private String resolveUri(String uri, String sessionId) throws Exception {
        if (uri.equals("echotik://regions")) {
            return toJson(FieldDictionary.REGIONS);
        }

        if (uri.startsWith("echotik://categories/")) {
            String region = uri.substring("echotik://categories/".length());
            if (!SAFE_URI_PARAM.matcher(region).matches()) {
                return "{\"error\": \"Invalid region parameter\"}";
            }
            return getCategories(region);
        }

        if (uri.startsWith("echotik://fields/")) {
            String entityType = uri.substring("echotik://fields/".length());
            if (!SAFE_URI_PARAM.matcher(entityType).matches()) {
                return "{\"error\": \"Invalid entity_type parameter\"}";
            }
            return getFieldDict(entityType);
        }

        if (uri.equals("echotik://endpoints")) {
            return getEndpoints();
        }

        if (uri.equals("echotik://connection-rules")) {
            return getConnectionRules();
        }

        if (uri.equals("echotik://scoring-algorithms")) {
            return getScoringAlgorithms();
        }

        if (uri.equals("chain://state")) {
            return getChainState(sessionId);
        }

        if (uri.equals("chain://cost-estimate")) {
            return getCostEstimate(sessionId);
        }

        return "{\"error\": \"Unknown resource URI: " + uri + "\"}";
    }

    private String getCategories(String region) throws Exception {
        List<EchotikCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<EchotikCategory>()
                        .eq(EchotikCategory::getRegion, region.toUpperCase())
                        .orderByAsc(EchotikCategory::getLevel)
                        .orderByAsc(EchotikCategory::getCategoryId));

        if (categories.isEmpty()) {
            return "{\"region\": \"" + region + "\", \"categories\": [], \"note\": \"该地区品类数据尚未导入\"}";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("region", region);
        result.put("total", categories.size());

        List<Map<String, Object>> list = new ArrayList<>();
        for (EchotikCategory cat : categories) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", cat.getCategoryId());
            item.put("name_zh", cat.getNameZh());
            item.put("name_en", cat.getNameEn());
            item.put("level", cat.getLevel());
            item.put("parent_id", cat.getParentId());
            list.add(item);
        }
        result.put("categories", list);
        return toJson(result);
    }

    private String getFieldDict(String entityType) throws Exception {
        List<String> fields = FieldDictionary.getFieldsForType(entityType);
        if (fields.isEmpty()) {
            return "{\"error\": \"Unknown entity_type: " + entityType + "\"}";
        }

        Map<String, String> descMap = FieldDictionary.getFieldDescForType(entityType);
        Map<String, Object> fieldDesc = new LinkedHashMap<>();
        for (String field : fields) {
            String desc = descMap.getOrDefault(field, field);
            fieldDesc.put(field, Map.of("type", inferFieldType(field), "desc_zh", desc));
        }

        Map<String, Object> result = Map.of(
                "entity_type", entityType,
                "field_count", fields.size(),
                "fields", fieldDesc
        );
        return toJson(result);
    }

    private String getEndpoints() throws Exception {
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (Map.Entry<String, String[]> e : FieldDictionary.SOURCE_TYPE_BLOCK_MAP.entrySet()) {
            Map<String, Object> ep = new LinkedHashMap<>();
            ep.put("source_type", e.getKey());
            ep.put("block_id", e.getValue()[0]);
            ep.put("output_type", e.getValue()[1]);
            endpoints.add(ep);
        }
        return toJson(Map.of("endpoints", endpoints));
    }

    private String getConnectionRules() throws Exception {
        Map<String, Object> rules = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : FieldDictionary.CONNECTION_RULES.entrySet()) {
            rules.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return toJson(Map.of("connection_rules", rules));
    }

    private String getScoringAlgorithms() throws Exception {
        List<Map<String, Object>> algorithms = List.of(
                Map.of("id", "linear_map", "name_zh", "线性映射",
                        "desc", "将字段值线性映射到[0,max_score]，适合：销量、GMV等越大越好的指标"),
                Map.of("id", "tier_map", "name_zh", "分段映射",
                        "desc", "按分段给分，适合：评分(1-5星)、佣金比例等离散值"),
                Map.of("id", "inverse_map", "name_zh", "逆向映射",
                        "desc", "值越小得分越高，适合：退款率、价格等越低越好的指标")
        );
        return toJson(Map.of(
                "numeric_algorithms", algorithms,
                "semantic_note", "semantic类型：LLM根据semantic_prompt对每批≤5个商品评分，消耗Token"
        ));
    }

    private String getChainState(String sessionId) throws Exception {
        if (sessionId == null) {
            return "{\"error\": \"session_id未提供\"}";
        }
        ChainBuildSession session = sessionManager.get(sessionId);
        if (session == null) {
            return "{\"error\": \"ChainBuildSession不存在: " + sessionId + "\"}";
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("session_id", sessionId);
        state.put("chain_length", session.getBlocks().size());
        state.put("has_data_source", session.isHasDataSource());
        state.put("current_output_type", session.getCurrentOutputType());
        state.put("available_fields", session.getAvailableFields());
        state.put("score_fields", session.getScoreFields());
        state.put("estimated_rows", session.getEstimatedRowCount());
        state.put("incremental_mode", session.isIncrementalMode());

        List<Map<String, Object>> blockSummary = new ArrayList<>();
        for (Map<String, Object> block : session.getBlocks()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("seq", block.get("seq"));
            s.put("blockId", block.get("blockId"));
            s.put("label", block.get("label"));
            blockSummary.add(s);
        }
        state.put("blocks", blockSummary);
        return toJson(state);
    }

    private String getCostEstimate(String sessionId) throws Exception {
        if (sessionId == null) {
            return "{\"api_calls\": 0, \"llm_tokens\": 0}";
        }
        ChainBuildSession session = sessionManager.get(sessionId);
        if (session == null) {
            return "{\"api_calls\": 0, \"llm_tokens\": 0}";
        }

        int apiCalls = 0;
        int llmTokens = 0;
        for (Map<String, Object> block : session.getBlocks()) {
            String blockId = (String) block.get("blockId");
            if (blockId != null && blockId.startsWith("SOURCE_")) apiCalls += 10;
            if (blockId != null && ("SCORE_SEMANTIC".equals(blockId) || "ANNOTATE_LLM_COMMENT".equals(blockId))) {
                llmTokens += 2000;
            }
        }

        return toJson(Map.of(
                "api_calls_estimate", apiCalls,
                "llm_tokens_estimate", llmTokens,
                "note", "API调用按每个DS块约10次估算；LLM Token按每批2000估算"
        ));
    }

    private String inferFieldType(String fieldName) {
        if (fieldName.endsWith("_cnt") || fieldName.endsWith("_count") ||
                fieldName.contains("_id") || fieldName.equals("review_count")) {
            return "integer";
        }
        if (fieldName.endsWith("_amt") || fieldName.contains("_price") ||
                fieldName.contains("_rate") || fieldName.contains("_ratio")) {
            return "number";
        }
        if (fieldName.endsWith("_time") || fieldName.endsWith("_dt") || fieldName.endsWith("_date")) {
            return "string(datetime)";
        }
        if (fieldName.contains("_flag")) {
            return "integer(enum)";
        }
        return "string";
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private Map<String, Object> errorContents(String uri, String message) {
        return Map.of("contents", List.of(Map.of(
                "uri", uri != null ? uri : "",
                "text", "{\"error\": \"" + message + "\"}"
        )));
    }
}
