package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.request.EchotikApiKeySaveRequest;
import com.tiktok.selection.dto.response.EchotikApiKeyVO;
import com.tiktok.selection.service.EchotikApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员 Echotik API 密钥池管理接口
 *
 * <p>apiKey/apiSecret 明文传入（HTTPS），服务层 AES-256-GCM 加密存库，
 * 查询时返回脱敏后4位。
 *
 * <ul>
 *   <li>GET    /api/admin/api-keys              — 查询全部密钥（脱敏）
 *   <li>GET    /api/admin/api-keys/{id}         — 查询单条（脱敏）
 *   <li>POST   /api/admin/api-keys              — 新增密钥
 *   <li>PUT    /api/admin/api-keys/{id}         — 更新密钥（apiKey/apiSecret留空则保留原值）
 *   <li>DELETE /api/admin/api-keys/{id}         — 删除密钥
 *   <li>PATCH  /api/admin/api-keys/{id}/toggle  — 切换启用/禁用状态
 * </ul>
 *
 * @author system
 * @date 2026/03/24
 */
@Validated
@RestController
@RequestMapping("/api/admin/api-keys")
@RequiredArgsConstructor
public class AdminApiKeyController {

    private static final String ID_PATTERN = "^[0-9a-zA-Z\\-]{1,64}$";
    private static final String ID_PATTERN_MSG = "id格式无效";

    private final EchotikApiKeyService echotikApiKeyService;

    @GetMapping
    public R<List<EchotikApiKeyVO>> list() {
        return R.ok(echotikApiKeyService.listAllVO());
    }

    @GetMapping("/{id}")
    public R<EchotikApiKeyVO> get(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        return R.ok(echotikApiKeyService.getVO(id));
    }

    @PostMapping
    public R<EchotikApiKeyVO> create(@Valid @RequestBody EchotikApiKeySaveRequest request) {
        return R.ok(echotikApiKeyService.create(request));
    }

    @PutMapping("/{id}")
    public R<EchotikApiKeyVO> update(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id,
            @Valid @RequestBody EchotikApiKeySaveRequest request) {
        return R.ok(echotikApiKeyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        echotikApiKeyService.delete(id);
        return R.ok();
    }

    @PatchMapping("/{id}/toggle")
    public R<EchotikApiKeyVO> toggle(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        return R.ok(echotikApiKeyService.toggleActive(id));
    }
}
