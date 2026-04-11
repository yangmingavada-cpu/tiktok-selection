package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户增列更新请求（重命名/改 options）
 * 用于 PATCH /sessions/{id}/extra-cols/{colId}
 *
 * @author system
 * @date 2026/04/10
 */
@Data
public class ExtraColUpdateRequest {

    /**
     * 新列名（可空，留空则不改）
     */
    @Size(max = 50, message = "列名最多 50 字符")
    private String label;

    /**
     * 新可选项（可空，仅 tag 类型可改）
     */
    private List<String> options;
}
