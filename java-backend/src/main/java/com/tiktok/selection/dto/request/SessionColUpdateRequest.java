package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 列重命名请求
 * 用于 PATCH /sessions/{id}/cols/{field}
 *
 * @author system
 * @date 2026/04/11
 */
@Data
public class SessionColUpdateRequest {

    /**
     * 新的列显示名
     * 既适用于原始列（写入 currentView.dims[i].label）
     * 也适用于用户增列（写入 userExtraCols.cols[i].label）
     */
    @NotBlank(message = "label 不能为空")
    @Size(max = 50, message = "列名长度不能超过 50")
    private String label;
}
