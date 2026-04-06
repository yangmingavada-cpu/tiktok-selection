package com.tiktok.selection.controller;

import com.tiktok.selection.common.R;
import com.tiktok.selection.service.MemoryFileService;
import com.tiktok.selection.service.MemoryFileService.MemoryIndexEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 用户记忆文件内部接口（Python AI 服务专用）
 *
 * <p>认证：通过 McpAuthFilter 的 X-MCP-Token 头校验，与 MCP 端点共用同一 Token。
 * 路径前缀 /api/internal/ 已在 SecurityConfig 中 permitAll（绕过 JWT），
 * 但仍需通过 McpAuthFilter 的 Token 校验。
 *
 * <ul>
 *   <li>GET  /api/internal/memory/{userId}/index?sessionId=           — 查询记忆索引</li>
 *   <li>GET  /api/internal/memory/{userId}/index-path?sessionId=&scope= — 查询 MEMORY.md 路径</li>
 *   <li>POST /api/internal/memory/{userId}/read-files                 — 批量读取文件内容</li>
 *   <li>POST /api/internal/memory/{userId}/write                      — 写入一条记忆</li>
 * </ul>
 *
 * <p>多 Agent 扩展说明：
 * {@code agentType} 字段记录写入来源（main/distillation/worker_xxx），
 * 支持未来 Coordinator 模式下多 Worker 并行写入的归因追溯。
 *
 * @author system
 * @date 2026/04/01
 */
@RestController
@RequestMapping("/api/internal/memory")
@RequiredArgsConstructor
public class MemoryFileController {

    private final MemoryFileService memoryFileService;

    /**
     * 查询记忆索引。
     * sessionId=null → 只返回 common 记忆；sessionId 不为空 → 返回 common + session 记忆。
     */
    @GetMapping("/{userId}/index")
    public R<List<MemoryIndexEntry>> index(
            @PathVariable String userId,
            @RequestParam(required = false) String sessionId) {
        return R.ok(memoryFileService.listIndex(userId, sessionId));
    }

    /**
     * 查询指定 scope 的 MEMORY.md 相对路径（从 DB 读取）。
     * 用于 Agent 直接定位索引文件，无需按约定拼路径。
     */
    @GetMapping("/{userId}/index-path")
    public R<String> indexPath(
            @PathVariable String userId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "common") String scope) {
        return R.ok(memoryFileService.getIndexPath(userId, sessionId, scope));
    }

    /**
     * 批量读取文件内容。
     * filePaths 来自 listIndex 返回的 filePath 字段。
     */
    @PostMapping("/{userId}/read-files")
    public R<Map<String, String>> readFiles(
            @PathVariable String userId,
            @RequestBody ReadFilesRequest request) throws IOException {
        return R.ok(memoryFileService.readFiles(userId, request.filePaths()));
    }

    /**
     * 写入一条记忆文件并更新索引。
     * 幂等：同名文件会覆盖写入（DB 记录也会更新）。
     */
    @PostMapping("/{userId}/write")
    public R<String> write(
            @PathVariable String userId,
            @RequestBody WriteMemoryRequest request) throws IOException {
        String filePath = memoryFileService.writeMemory(
                userId,
                request.sessionId(),
                request.scope(),
                request.memoryType(),
                request.name(),
                request.description(),
                request.content(),
                request.agentType() != null ? request.agentType() : "main"
        );
        return R.ok(filePath);
    }

    // ─── 请求 DTO ───────────────────────────────────────────────────────────

    public record ReadFilesRequest(List<String> filePaths) {}

    /**
     * 写入记忆请求。
     *
     * @param sessionId  NULL = common 跨会话记忆
     * @param scope      "common" | "session"
     * @param memoryType user / feedback / project / reference
     * @param name       记忆名称
     * @param description 一句话摘要（用于未来相关性筛选）
     * @param content    记忆正文 Markdown
     * @param agentType  写入来源（main/distillation/worker_xxx）
     */
    public record WriteMemoryRequest(
            String sessionId,
            String scope,
            String memoryType,
            String name,
            String description,
            String content,
            String agentType
    ) {}
}
