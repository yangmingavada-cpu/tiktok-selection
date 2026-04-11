package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 删除多行请求
 * 用于 POST /sessions/{id}/rows:delete
 *
 * @author system
 * @date 2026/04/11
 */
@Data
public class SessionRowsDeleteRequest {

    /**
     * 要删除的行下标列表（原始数据数组下标，非可视下标）
     * 后端会去重 + 校验越界 + 按从大到小顺序删除以避免 index shift
     */
    @NotEmpty(message = "rowIndices 不能为空")
    @Size(max = 5000, message = "一次最多删除 5000 行")
    private List<@NotNull @Min(0) Integer> rowIndices;
}
