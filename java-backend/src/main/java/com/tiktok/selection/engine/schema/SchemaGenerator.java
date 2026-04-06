package com.tiktok.selection.engine.schema;

import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.annotation.McpParam;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动从 {@link McpBlock}/{@link McpParam} 注解生成 JSON Schema。
 * 用于 McpToolRegistry 的 modify_block 工具描述，以及后续文档生成。
 *
 * @author system
 * @date 2026/03/26
 */
public final class SchemaGenerator {

    private SchemaGenerator() {
    }

    /**
     * 从带 {@link McpParam} 注解的 Request 类生成 JSON Schema（Map 形式）。
     *
     * @param requestClass 带 @McpBlock / @McpParam 注解的 Request 类
     * @return JSON Schema Map，可直接序列化为 JSON
     */
    public static Map<String, Object> fromClass(Class<?> requestClass) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Field field : requestClass.getFields()) {
            McpParam param = field.getAnnotation(McpParam.class);
            if (param == null) {
                continue;
            }

            Map<String, Object> propSchema = new LinkedHashMap<>();
            propSchema.put("type", param.type());
            propSchema.put("description", param.desc());

            if (param.enumValues().length > 0) {
                propSchema.put("enum", Arrays.asList(param.enumValues()));
            }
            if (!param.defaultValue().isEmpty()) {
                propSchema.put("default", param.defaultValue());
            }
            if (!param.example().isEmpty()) {
                propSchema.put("example", param.example());
            }

            properties.put(field.getName(), propSchema);

            if (param.required()) {
                required.add(field.getName());
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    /**
     * 生成单个块类型的可读文本描述，供 LLM 的 modify_block 工具说明使用。
     *
     * <p>格式示例：
     * <pre>
     * DS01（product/list 商品列表筛选）：region*(必填), category_id, page_size(默认10)
     * </pre>
     *
     * @param requestClass 带 @McpBlock / @McpParam 注解的 Request 类
     * @return 单行文本描述
     */
    public static String describeBlock(Class<?> requestClass) {
        McpBlock meta = requestClass.getAnnotation(McpBlock.class);
        if (meta == null) {
            return requestClass.getSimpleName();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(meta.blockId());
        if (!meta.endpoint().isEmpty()) {
            sb.append("（").append(meta.endpoint()).append(" ").append(meta.description()).append("）");
        } else if (!meta.description().isEmpty()) {
            sb.append("（").append(meta.description()).append("）");
        }
        sb.append("：");

        List<String> paramParts = new ArrayList<>();
        for (Field field : requestClass.getFields()) {
            McpParam param = field.getAnnotation(McpParam.class);
            if (param == null) {
                continue;
            }
            StringBuilder part = new StringBuilder(field.getName());
            if (param.required()) {
                part.append("*");
            }
            if (!param.defaultValue().isEmpty()) {
                part.append("(默认").append(param.defaultValue()).append(")");
            }
            if (param.enumValues().length > 0) {
                part.append("[").append(String.join("|", param.enumValues())).append("]");
            }
            paramParts.add(part.toString());
        }
        sb.append(String.join(", ", paramParts));
        return sb.toString();
    }

    /**
     * 批量生成多个块类型的可读文本描述，每个块占一行。
     *
     * @param requestClasses 多个 Request 类
     * @return 多行文本
     */
    public static String describeBlocks(Class<?>... requestClasses) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> clazz : requestClasses) {
            sb.append("- ").append(describeBlock(clazz)).append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
