package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.response.CategoryTreeResponse;
import com.tiktok.selection.entity.EchotikCategory;
import com.tiktok.selection.service.CategoryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类控制器，提供分类树形查询与搜索接口
 *
 * @author system
 * @date 2026/03/22
 */
@Validated
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 获取指定区域的分类树形结构
     *
     * @param region 区域编码
     * @return 树形分类列表
     */
    @GetMapping("/tree")
    public R<List<CategoryTreeResponse>> getCategoryTree(@RequestParam @NotBlank @Size(max = 10) String region) {
        return R.ok(categoryService.getCategoryTree(region));
    }

    /**
     * 搜索分类
     *
     * @param region 区域编码
     * @param nameZh 中文名（模糊匹配）
     * @return 匹配的分类列表
     */
    @GetMapping("/search")
    public R<List<EchotikCategory>> searchCategory(@RequestParam @NotBlank @Size(max = 10) String region,
                                                   @RequestParam @NotBlank @Size(max = 100) String nameZh) {
        return R.ok(categoryService.searchCategory(region, nameZh));
    }
}
