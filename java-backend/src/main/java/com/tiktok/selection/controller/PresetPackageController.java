package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.response.PresetPackageVO;
import com.tiktok.selection.service.PresetPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端官方方案库接口（只读）
 *
 * <p>已登录用户可通过 GET /api/preset-packages 浏览所有 active 的官方预设方案，
 * 然后通过 POST /api/plans/from-preset/{presetId} 把官方方案克隆到自己的方案库。
 *
 * <p>管理员维护预设见 {@link AdminPresetController}。
 *
 * @author system
 * @date 2026/05/05
 */
@RestController
@RequestMapping("/api/preset-packages")
@RequiredArgsConstructor
public class PresetPackageController {

    private final PresetPackageService presetPackageService;

    @GetMapping
    public R<List<PresetPackageVO>> list() {
        return R.ok(presetPackageService.listActiveVO());
    }
}
