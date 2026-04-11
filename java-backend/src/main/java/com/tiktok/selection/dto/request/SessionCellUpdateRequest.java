package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单元格编辑请求
 * 用于 PATCH /sessions/{id}/cells
 *
 * @author system
 * @date 2026/04/10
 */
@Data
public class SessionCellUpdateRequest {

    /**
     * 行下标（原始数据数组的下标，非可视下标）
     */
    @NotNull(message = "rowIndex 不能为空")
    @Min(value = 0, message = "rowIndex 不能为负")
    private Integer rowIndex;

    /**
     * 列字段 id
     * - 原始列：currentView.dims 里的 col.id
     * - 用户增列：以 user_ 前缀开头
     */
    @NotBlank(message = "field 不能为空")
    private String field;

    /**
     * 单元格新值。string/number 都可能，按 dim.type 校验与标准化。
     */
    private Object value;
}
