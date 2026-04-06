package com.tiktok.selection.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 分类搜索响应DTO
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@Builder
public class CategorySearchResponse {

    private String categoryId;
    private String region;
    private String parentId;
    private Integer level;
    private String nameEn;
    private String nameZh;
}
