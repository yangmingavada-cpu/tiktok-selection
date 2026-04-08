package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.request.LlmConfigSaveRequest;
import com.tiktok.selection.dto.response.LlmConfigVO;
import com.tiktok.selection.service.IntentService;
import com.tiktok.selection.service.LlmConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员 LLM配置管理接口
 *
 * <p>路径：/api/admin/llm-config（已受 Spring Security ADMIN 角色保护）
 *
 * <ul>
 *   <li>POST   /api/admin/llm-config        — 新建配置（apiKey 明文传入，服务层加密存库）
 *   <li>PUT    /api/admin/llm-config/{id}   — 更新配置（apiKey 留空则保留原有Key）
 *   <li>GET    /api/admin/llm-config        — 查询全部（apiKey 脱敏返回后4位）
 *   <li>GET    /api/admin/llm-config/{id}   — 查询单条（apiKey 脱敏）
 *   <li>DELETE /api/admin/llm-config/{id}   — 删除配置
 * </ul>
 *
 * @author system
 * @date 2026/03/24
 */
@Validated
@RestController
@RequestMapping("/api/admin/llm-config")
@RequiredArgsConstructor
public class AdminLlmConfigController {

    private final LlmConfigService llmConfigService;
    private final IntentService intentService;

    @GetMapping
    public R<List<LlmConfigVO>> list() {
        return R.ok(llmConfigService.listAllVO());
    }

    // P2-2修复：@PathVariable id 校验格式，防超长字符串直达DB（规约第4条）
    private static final String ID_PATTERN = "^[0-9a-zA-Z\\-]{1,64}$";
    private static final String ID_PATTERN_MSG = "id格式无效";

    @GetMapping("/{id}")
    public R<LlmConfigVO> get(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        return R.ok(llmConfigService.getVO(id));
    }

    @PostMapping
    public R<LlmConfigVO> create(@Valid @RequestBody LlmConfigSaveRequest request) {
        return R.ok(llmConfigService.create(request));
    }

    @PutMapping("/{id}")
    public R<LlmConfigVO> update(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id,
            @Valid @RequestBody LlmConfigSaveRequest request) {
        return R.ok(llmConfigService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        llmConfigService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/test")
    public R<Map<String, Object>> test(
            @PathVariable @Pattern(regexp = ID_PATTERN, message = ID_PATTERN_MSG) String id) {
        return R.ok(intentService.testLlmConfig(id));
    }
}
