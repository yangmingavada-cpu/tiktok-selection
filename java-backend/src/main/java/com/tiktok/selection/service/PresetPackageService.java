package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.dto.request.PresetPackageSaveRequest;
import com.tiktok.selection.dto.response.PresetPackageVO;
import com.tiktok.selection.entity.PresetPackage;
import com.tiktok.selection.mapper.PresetPackageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 预设套餐服务
 *
 * <p>管理员通过此服务维护系统内置的积木链预设套餐，
 * 用户可在会话创建时选择预设套餐快速启动选品流程。
 *
 * @author system
 * @date 2026/03/24
 */
@Service
public class PresetPackageService extends ServiceImpl<PresetPackageMapper, PresetPackage> {

    private static final Logger log = LoggerFactory.getLogger(PresetPackageService.class);

    /** 积木链最大积木块数量（防超大对象占用内存） */
    private static final int BLOCK_CHAIN_MAX_SIZE = 200;
    /** 标签列表最大数量 */
    private static final int TAGS_MAX_SIZE = 20;

    // ==================== 用户端调用（只读） ====================

    /**
     * 查询所有已启用的预设套餐，按排序值升序返回（供用户端选择使用）
     */
    public List<PresetPackage> listActive() {
        return list(new LambdaQueryWrapper<PresetPackage>()
                .eq(PresetPackage::getActive, true)
                .orderByAsc(PresetPackage::getSortOrder));
    }

    /**
     * 查询所有已启用的预设套餐 VO 列表（供用户端 /api/preset-packages 接口）
     */
    public List<PresetPackageVO> listActiveVO() {
        return listActive().stream().map(this::toVO).toList();
    }

    // ==================== 管理员 CRUD ====================

    /**
     * 查询所有预设套餐（含禁用），按排序值升序返回 VO 列表
     */
    public List<PresetPackageVO> listAllVO() {
        List<PresetPackage> packages = list(new LambdaQueryWrapper<PresetPackage>()
                .orderByAsc(PresetPackage::getSortOrder));
        return packages.stream().map(this::toVO).toList();
    }

    /**
     * 按 ID 查询，返回 VO
     */
    public PresetPackageVO getVO(String id) {
        PresetPackage pkg = getById(id);
        if (pkg == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预设套餐不存在");
        }
        return toVO(pkg);
    }

    /**
     * 创建预设套餐
     */
    public PresetPackageVO create(PresetPackageSaveRequest req) {
        validateRequest(req);
        // 检查 pkgCode 唯一性
        long count = count(new LambdaQueryWrapper<PresetPackage>()
                .eq(PresetPackage::getPkgCode, req.getPkgCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "套餐代码已存在: " + req.getPkgCode());
        }

        PresetPackage pkg = new PresetPackage();
        fillFields(pkg, req);
        save(pkg);
        log.info("Preset package created: id={}, pkgCode={}", pkg.getId(), pkg.getPkgCode());
        return toVO(pkg);
    }

    /**
     * 更新预设套餐（全量更新）
     */
    public PresetPackageVO update(String id, PresetPackageSaveRequest req) {
        validateRequest(req);
        PresetPackage pkg = getById(id);
        if (pkg == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预设套餐不存在");
        }
        // pkgCode 变更时检查唯一性
        if (!req.getPkgCode().equals(pkg.getPkgCode())) {
            long count = count(new LambdaQueryWrapper<PresetPackage>()
                    .eq(PresetPackage::getPkgCode, req.getPkgCode()));
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "套餐代码已存在: " + req.getPkgCode());
            }
        }
        fillFields(pkg, req);
        updateById(pkg);
        log.info("Preset package updated: id={}, pkgCode={}", id, pkg.getPkgCode());
        return toVO(pkg);
    }

    /**
     * 删除预设套餐（物理删除）
     */
    public void delete(String id) {
        PresetPackage pkg = getById(id);
        if (pkg == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预设套餐不存在");
        }
        removeById(id);
        log.info("Preset package deleted: id={}, pkgCode={}", id, pkg.getPkgCode());
    }

    // ==================== 私有工具 ====================

    /**
     * 参数防护：限制集合大小，防止超大请求导致OOM（规约第4条）
     */
    private void validateRequest(PresetPackageSaveRequest req) {
        if (req.getBlockChain() != null && req.getBlockChain().size() > BLOCK_CHAIN_MAX_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "积木链最多允许" + BLOCK_CHAIN_MAX_SIZE + "个积木块");
        }
        if (req.getTags() != null && req.getTags().size() > TAGS_MAX_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "标签最多允许" + TAGS_MAX_SIZE + "个");
        }
    }

    private void fillFields(PresetPackage pkg, PresetPackageSaveRequest req) {
        pkg.setPkgCode(req.getPkgCode());
        pkg.setNameZh(req.getNameZh());
        pkg.setNameEn(req.getNameEn());
        pkg.setDescription(req.getDescription());
        pkg.setBlockChain(req.getBlockChain());
        pkg.setTags(req.getTags());
        if (req.getSortOrder() != null) {
            pkg.setSortOrder(req.getSortOrder());
        }
        if (req.getActive() != null) {
            pkg.setActive(req.getActive());
        }
    }

    private PresetPackageVO toVO(PresetPackage pkg) {
        return PresetPackageVO.builder()
                .id(pkg.getId())
                .pkgCode(pkg.getPkgCode())
                .nameZh(pkg.getNameZh())
                .nameEn(pkg.getNameEn())
                .description(pkg.getDescription())
                .blockChain(pkg.getBlockChain())
                .tags(pkg.getTags())
                .useCount(pkg.getUseCount())
                .active(pkg.getActive())
                .sortOrder(pkg.getSortOrder())
                .createTime(pkg.getCreateTime())
                .updateTime(pkg.getUpdateTime())
                .build();
    }
}
