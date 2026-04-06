package com.tiktok.selection.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具 JSON Schema 构建辅助方法。
 * 所有 ToolDefs 类共用，提供 fluent 风格的 schema/prop 构建。
 */
public final class ToolSchemaHelper {

    public static final List<String> REGIONS =
            List.of("TH", "US", "UK", "ID", "MY", "PH", "VN", "SG", "SA", "AE");

    public static final String RANKING_DATE_PARAM = "ranking_date";
    public static final String RANKING_DATE_DESC =
            "指定历史榜单日期(yyyy-MM-dd)，优先于ranking_period自动计算；用户指定具体日期时使用";

    private ToolSchemaHelper() {}

    public static Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("name", name);
        t.put("description", description);
        t.put("inputSchema", inputSchema);
        return t;
    }

    public static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", properties);
        s.put("required", required);
        return s;
    }

    @SafeVarargs
    public static Map<String, Object> props(Map<String, Object>... propMaps) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> p : propMaps) {
            result.putAll(p);
        }
        return result;
    }

    /** 带 enum 的属性 */
    public static Map<String, Object> prop(String name, String type, String desc, List<String> enumValues) {
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("description", desc);
        schema.put("enum", enumValues);
        p.put(name, schema);
        return p;
    }

    /** 必填属性（无 enum） */
    public static Map<String, Object> propReq(String name, String type, String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("description", desc);
        p.put(name, schema);
        return p;
    }

    /** 可选属性（带静态 enum） */
    public static Map<String, Object> propOpt(String name, String type, String desc, List<String> enumValues) {
        return prop(name, type, desc, enumValues);
    }

    /** 可选属性（无 enum） */
    public static Map<String, Object> propOpt(String name, String type, String desc) {
        return propReq(name, type, desc);
    }

    /** 动态 enum 属性（来自 available_fields） */
    public static Map<String, Object> propDynEnum(String name, String desc, List<String> availableFields) {
        return propDynEnum(name, desc, availableFields, null);
    }

    /** 动态 enum 属性（含字段中文描述，帮助 LLM 精准匹配字段名） */
    public static Map<String, Object> propDynEnum(String name, String desc,
                                                   List<String> availableFields,
                                                   Map<String, String> fieldDescriptions) {
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        if (fieldDescriptions != null && !fieldDescriptions.isEmpty() && !availableFields.isEmpty()) {
            StringBuilder sb = new StringBuilder(desc);
            sb.append("\n字段说明：");
            for (String field : availableFields) {
                String fieldDesc = fieldDescriptions.get(field);
                if (fieldDesc != null) {
                    sb.append("\n- ").append(field).append(": ").append(fieldDesc);
                }
            }
            schema.put("description", sb.toString());
        } else {
            schema.put("description", desc);
        }
        if (!availableFields.isEmpty()) {
            schema.put("enum", availableFields);
        }
        p.put(name, schema);
        return p;
    }

    /** 可选动态 enum 属性 */
    public static Map<String, Object> propOptDynEnum(String name, String desc, List<String> availableFields) {
        return propDynEnum(name, desc, availableFields, null);
    }

    /** 可选动态 enum 属性（含字段描述） */
    public static Map<String, Object> propOptDynEnum(String name, String desc,
                                                      List<String> availableFields,
                                                      Map<String, String> fieldDescriptions) {
        return propDynEnum(name, desc, availableFields, fieldDescriptions);
    }

    /** 可选动态 enum 数组属性 */
    public static Map<String, Object> propOptArrayDynEnum(String name, String desc, List<String> availableFields) {
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("description", desc);
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "string");
        if (!availableFields.isEmpty()) {
            items.put("enum", availableFields);
        }
        schema.put("items", items);
        p.put(name, schema);
        return p;
    }

    /** 可选字符串数组属性（无 enum 约束） */
    public static Map<String, Object> propOptArrayStr(String name, String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("description", desc);
        schema.put("items", Map.of("type", "string"));
        p.put(name, schema);
        return p;
    }

    /** 任意类型属性 */
    public static Map<String, Object> propAny(String name, String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("description", desc);
        p.put(name, schema);
        return p;
    }
}
