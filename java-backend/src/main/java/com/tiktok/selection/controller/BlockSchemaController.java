package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.engine.annotation.McpBlock;
import com.tiktok.selection.engine.schema.BlockClassRegistry;
import com.tiktok.selection.engine.schema.SchemaGenerator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Block Schema 暴露接口
 *
 * <p>给前端"方案库执行前可视化编辑"功能使用：前端拿到每个 block 的字段元数据
 * （字段名、类型、必填、枚举值、默认值、描述），按 schema 动态渲染表单。
 *
 * <p>schema 数据来源是 {@link com.tiktok.selection.engine.annotation.McpParam} 注解的反射，
 * 与 LLM 工具用的同一份元数据，零冗余。
 *
 * @author system
 * @date 2026/04/11
 */
@RestController
@RequestMapping("/api/blocks")
public class BlockSchemaController {

    /**
     * 拉取 block schema
     *
     * @param ids 逗号分隔的 blockId 列表，留空时返回全部
     * @return blockId → {blockId, label, endpoint, outputType, schema}
     */
    @GetMapping("/schemas")
    public R<Map<String, Object>> getSchemas(@RequestParam(required = false) List<String> ids) {
        Map<String, Object> result = new LinkedHashMap<>();

        Iterable<String> wanted = (ids == null || ids.isEmpty())
                ? BlockClassRegistry.BLOCKS.keySet()
                : ids;

        for (String id : wanted) {
            Class<?> cls = BlockClassRegistry.get(id);
            if (cls == null) continue;
            McpBlock meta = cls.getAnnotation(McpBlock.class);

            Map<String, Object> blockSchema = new LinkedHashMap<>();
            blockSchema.put("blockId", id);
            blockSchema.put("label", meta != null && !meta.description().isEmpty() ? meta.description() : id);
            blockSchema.put("endpoint", meta != null ? meta.endpoint() : "");
            blockSchema.put("outputType", meta != null ? meta.outputType() : "");
            blockSchema.put("schema", SchemaGenerator.fromClass(cls));

            result.put(id, blockSchema);
        }

        return R.ok(result);
    }
}
