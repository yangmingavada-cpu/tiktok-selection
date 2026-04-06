package com.tiktok.selection.engine;

import java.util.Map;

/**
 * Executor 配置读取工具类
 * 支持 camelCase / snake_case 双命名兼容：LLM 生成的 config 使用 snake_case，
 * 旧 Executor 使用 camelCase，此工具类按顺序尝试所有候选 key，返回第一个非 null 值。
 *
 * @author system
 * @date 2026/03/25
 */
public final class ConfigUtil {

    private ConfigUtil() {}

    /**
     * 按顺序尝试多个 key，返回第一个非 null 值；全部为 null 时返回 null。
     *
     * @param config config Map（来自 BlockContext.getBlockConfig()）
     * @param keys   候选 key 列表，建议先写 snake_case，再写 camelCase
     */
    public static Object get(Map<String, Object> config, String... keys) {
        for (String key : keys) {
            Object val = config.get(key);
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    /** 读取字符串，找不到返回 null */
    public static String getString(Map<String, Object> config, String... keys) {
        Object val = get(config, keys);
        return val != null ? val.toString() : null;
    }

    /** 读取整数，找不到返回 defaultValue */
    public static int getInt(Map<String, Object> config, int defaultValue, String... keys) {
        Object val = get(config, keys);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
