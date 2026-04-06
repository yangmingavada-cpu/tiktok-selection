package com.tiktok.selection.common;

/**
 * 数据脱敏工具类
 *
 * @author system
 * @date 2026/03/22
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {
    }

    /**
     * 邮箱脱敏：保留首字符和@后域名，中间用***替代
     * 例：username@domain.com -> u***@domain.com
     *
     * @param email 原始邮箱
     * @return 脱敏后的邮箱，null或空字符串原样返回
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
