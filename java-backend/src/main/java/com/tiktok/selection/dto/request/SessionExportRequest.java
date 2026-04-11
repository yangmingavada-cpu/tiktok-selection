package com.tiktok.selection.dto.request;

import lombok.Data;

/**
 * 选品结果导出请求（GET query 参数）
 * 用于 GET /sessions/{id}/export
 *
 * 所有字段都为可选；全部为空时回退到全量导出（向后兼容）。
 *
 * @author system
 * @date 2026/04/10
 */
@Data
public class SessionExportRequest {

    /**
     * 文件格式：xlsx（默认）| csv
     */
    private String format = "xlsx";

    /**
     * 要导出的列 id 列表，逗号分隔；null/空 = 全部列
     */
    private String fields;

    /**
     * 排序：colId:asc 或 colId:desc；null = 不排序
     */
    private String order;

    /**
     * 全表搜索关键字；null/空 = 不过滤
     */
    private String search;

    /**
     * 仅导出指定的原数组下标行，逗号分隔；null/空 = 不限
     */
    private String rowIndices;

    /**
     * 列名重命名映射：colId1:别名1,colId2:别名2 形式
     */
    private String renames;
}
