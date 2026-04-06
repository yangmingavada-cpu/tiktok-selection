package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.response.McpToolVO;
import com.tiktok.selection.service.McpToolConfigService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员 MCP工具管理接口
 *
 * <ul>
 *   <li>GET    /api/admin/mcp/tools              — 查询所有工具（可按tag筛选）
 *   <li>POST   /api/admin/mcp/tools/{name}/ban   — 禁用指定工具
 *   <li>POST   /api/admin/mcp/tools/{name}/unban — 启用指定工具
 *   <li>POST   /api/admin/mcp/tools/tag/{tag}/ban — 批量禁用某标签下所有工具
 * </ul>
 *
 * @author system
 * @date 2026/04/01
 */
@Validated
@RestController
@RequestMapping("/api/admin/mcp/tools")
@RequiredArgsConstructor
public class AdminMcpToolController {

    private static final String TOOL_NAME_PATTERN = "^[a-z_]{1,64}$";
    private static final String TOOL_NAME_MSG     = "toolName只能包含小写字母和下划线";
    private static final String TAG_PATTERN       = "^[a-z]{1,32}$";
    private static final String TAG_MSG           = "tag只能包含小写字母";

    private final McpToolConfigService toolConfigService;

    @GetMapping
    public R<List<McpToolVO>> list(@RequestParam(required = false) String tag) {
        List<McpToolVO> all = toolConfigService.listForAdmin();
        if (tag != null) {
            all = all.stream().filter(v -> tag.equals(v.tag())).toList();
        }
        return R.ok(all);
    }

    @PostMapping("/{toolName}/ban")
    public R<Void> ban(
            @PathVariable @Pattern(regexp = TOOL_NAME_PATTERN, message = TOOL_NAME_MSG) String toolName,
            @RequestBody(required = false) BanRequest request) {
        toolConfigService.ban(toolName, request != null ? request.reason() : null, operatorId());
        return R.ok();
    }

    @PostMapping("/{toolName}/unban")
    public R<Void> unban(
            @PathVariable @Pattern(regexp = TOOL_NAME_PATTERN, message = TOOL_NAME_MSG) String toolName) {
        toolConfigService.unban(toolName, operatorId());
        return R.ok();
    }

    @PostMapping("/tag/{tag}/ban")
    public R<Void> banByTag(
            @PathVariable @Pattern(regexp = TAG_PATTERN, message = TAG_MSG) String tag,
            @RequestBody(required = false) BanRequest request) {
        toolConfigService.banByTag(tag, request != null ? request.reason() : null, operatorId());
        return R.ok();
    }

    // ── 工具方法 ───────────────────────────────────────────────────────────────

    private String operatorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    public record BanRequest(String reason) {}
}
