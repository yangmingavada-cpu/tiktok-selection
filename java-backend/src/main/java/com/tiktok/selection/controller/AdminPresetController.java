package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.request.PresetPackageSaveRequest;
import com.tiktok.selection.dto.response.PresetPackageVO;
import com.tiktok.selection.service.PresetPackageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员预设套餐管理接口
 *
 * <ul>
 *   <li>GET    /api/admin/presets        — 查询全部套餐（含禁用）
 *   <li>GET    /api/admin/presets/{id}   — 查询单条
 *   <li>POST   /api/admin/presets        — 新建套餐
 *   <li>PUT    /api/admin/presets/{id}   — 更新套餐
 *   <li>DELETE /api/admin/presets/{id}   — 删除套餐
 * </ul>
 *
 * @author system
 * @date 2026/03/24
 */
@Validated
@RestController
@RequestMapping("/api/admin/presets")
@RequiredArgsConstructor
public class AdminPresetController {

    private static final String ID_PATTERN = "^[0-9a-zA-Z\\-]{1,64}$";
    private static final String ID_PATTERN_MSG = "id格式无效";

    private final PresetPackageService presetPackageService;

    @GetMapping
    public R<List<PresetPackageVO>> list() {
        return R.ok(presetPackageService.listAllVO());
    }

    @GetMapping("/{id}")
    public R<PresetPackageVO> get(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        return R.ok(presetPackageService.getVO(id));
    }

    @PostMapping
    public R<PresetPackageVO> create(@Valid @RequestBody PresetPackageSaveRequest request) {
        return R.ok(presetPackageService.create(request));
    }

    @PutMapping("/{id}")
    public R<PresetPackageVO> update(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id,
            @Valid @RequestBody PresetPackageSaveRequest request) {
        return R.ok(presetPackageService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        presetPackageService.delete(id);
        return R.ok();
    }
}
