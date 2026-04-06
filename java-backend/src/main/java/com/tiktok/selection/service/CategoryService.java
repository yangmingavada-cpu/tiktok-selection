package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tiktok.selection.dto.response.CategoryTreeResponse;
import com.tiktok.selection.entity.EchotikCategory;
import com.tiktok.selection.mapper.EchotikCategoryMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类服务，提供Echotik分类的树形查询与搜索
 *
 * @author system
 * @date 2026/03/22
 */
@Service
public class CategoryService {

    private final EchotikCategoryMapper echotikCategoryMapper;

    public CategoryService(EchotikCategoryMapper echotikCategoryMapper) {
        this.echotikCategoryMapper = echotikCategoryMapper;
    }

    /**
     * 获取指定区域的分类树形结构
     *
     * @param region 区域编码
     * @return 树形分类列表
     */
    @Cacheable(value = "categories", key = "#region")
    public List<CategoryTreeResponse> getCategoryTree(String region) {
        List<EchotikCategory> allCategories = listByRegion(region);
        return buildTree(allCategories);
    }

    /**
     * 获取指定区域的全部分类（扁平列表）
     *
     * @param region 区域编码
     * @return 分类列表
     */
    public List<EchotikCategory> listByRegion(String region) {
        return echotikCategoryMapper.selectList(
                new LambdaQueryWrapper<EchotikCategory>()
                        .eq(EchotikCategory::getRegion, region));
    }

    /**
     * 按区域和中文名搜索分类
     *
     * @param region 区域编码
     * @param nameZh 中文名（模糊匹配）
     * @return 匹配的分类列表
     */
    public List<EchotikCategory> searchCategory(String region, String nameZh) {
        return echotikCategoryMapper.selectList(
                new LambdaQueryWrapper<EchotikCategory>()
                        .eq(EchotikCategory::getRegion, region)
                        .like(EchotikCategory::getNameZh, nameZh));
    }

    /**
     * 将扁平分类列表组装为树形结构
     *
     * @param categories 扁平分类列表
     * @return 树形分类响应列表
     */
    private List<CategoryTreeResponse> buildTree(List<EchotikCategory> categories) {
        Map<String, List<EchotikCategory>> groupByParent = categories.stream()
                .collect(Collectors.groupingBy(
                        cat -> cat.getParentId() != null ? cat.getParentId() : ""));

        List<CategoryTreeResponse> roots = new ArrayList<>();
        List<EchotikCategory> rootCategories = groupByParent.getOrDefault("", new ArrayList<>());

        for (EchotikCategory root : rootCategories) {
            roots.add(buildNode(root, groupByParent));
        }
        return roots;
    }

    /**
     * 递归构建树节点
     *
     * @param category      当前分类
     * @param groupByParent 按父ID分组的映射
     * @return 树节点
     */
    private CategoryTreeResponse buildNode(EchotikCategory category,
                                           Map<String, List<EchotikCategory>> groupByParent) {
        CategoryTreeResponse node = new CategoryTreeResponse();
        node.setCategoryId(category.getCategoryId());
        node.setRegion(category.getRegion());
        node.setParentId(category.getParentId());
        node.setLevel(category.getLevel());
        node.setNameEn(category.getNameEn());
        node.setNameZh(category.getNameZh());

        List<EchotikCategory> children = groupByParent.getOrDefault(
                category.getCategoryId(), new ArrayList<>());
        List<CategoryTreeResponse> childNodes = new ArrayList<>();
        for (EchotikCategory child : children) {
            childNodes.add(buildNode(child, groupByParent));
        }
        node.setChildren(childNodes);
        return node;
    }
}
