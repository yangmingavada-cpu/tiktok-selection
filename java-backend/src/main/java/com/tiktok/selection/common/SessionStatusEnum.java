package com.tiktok.selection.common;

/**
 * 会话状态枚举
 *
 * @author system
 * @date 2026/03/22
 */
public enum SessionStatusEnum {

    CREATED("created"),
    IN_PROGRESS("in_progress"),
    PAUSED("paused"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    ARCHIVED("archived");

    private final String value;

    SessionStatusEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
