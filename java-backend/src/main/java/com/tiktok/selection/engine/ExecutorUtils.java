package com.tiktok.selection.engine;

/**
 * 执行器共享工具方法，遵循DRY原则（设计规约第14条）。
 * 供各BlockExecutor实现类调用，避免在多个类中重复实现相同逻辑。
 *
 * @author system
 * @date 2026/03/24
 */
public final class ExecutorUtils {

    private ExecutorUtils() {
        // 工具类不允许实例化
    }

    /**
     * 将对象安全转换为double，null或非数值字符串返回0。
     *
     * @param value the value to convert
     * @return the double representation, or 0 if null/unparseable
     */
    public static double toDouble(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 判断对象是否可解析为数值（Number实例或数值字符串）。
     *
     * @param value the value to check
     * @return true if the value is numeric
     */
    public static boolean isNumeric(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number) {
            return true;
        }
        try {
            Double.parseDouble(value.toString());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
