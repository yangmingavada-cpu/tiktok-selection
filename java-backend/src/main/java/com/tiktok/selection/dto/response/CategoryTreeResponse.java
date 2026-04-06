package com.tiktok.selection.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 分类树形响应DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
public class CategoryTreeResponse {

    private String categoryId;

    private String region;

    private String parentId;

    private Integer level;

    private String nameEn;

    private String nameZh;

    /**
     * 子分类列表
     */
    private List<CategoryTreeResponse> children;
}
