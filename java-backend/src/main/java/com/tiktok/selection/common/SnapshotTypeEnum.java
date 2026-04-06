package com.tiktok.selection.common;

/**
 * 快照类型枚举
 *
 * @author system
 * @date 2026/03/22
 */
public enum SnapshotTypeEnum {

    FULL_DATA("full_data"),
    IDS_ONLY("ids_only");

    private final String value;

    SnapshotTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
