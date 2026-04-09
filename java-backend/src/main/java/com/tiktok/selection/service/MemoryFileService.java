package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tiktok.selection.entity.UserMemoryFile;
import com.tiktok.selection.mapper.UserMemoryFileMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tiktok.selection.common.FileLockHelper;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户记忆文件服务：本地磁盘读写 + DB索引维护。
 *
 * <p>设计要点：
 * <ul>
 *   <li>文件内容存磁盘，DB只存元数据（name/description/type）用于相关性筛选</li>
 *   <li>per-userId ReentrantLock 保证同一用户的并发写入安全（为多Agent扩展预留）</li>
 *   <li>存储层通过 {@code memory.root} 配置隔离，迁移 OSS 时只需替换本 Service 实现</li>
 *   <li>路径安全：所有文件路径强制限定在 userId 目录下，防路径穿越</li>
 * </ul>
 *
 * 目录结构：
 * <pre>
 * {memory_root}/{userId}/
 *   common/
 *     MEMORY.md                          ← 始终加载的索引
 *     user_preferences.md
 *   select_product/{sessionId}/memory/
 *     MEMORY.md
 *     session_chain.md
 * </pre>
 *
 * @author system
 * @date 2026/04/01
 */
@Service
@RequiredArgsConstructor
public class MemoryFileService {

    private static final Logger log = LoggerFactory.getLogger(MemoryFileService.class);

    @Value("${memory.root:/data/memory}")
    private String memoryRoot;

    private final UserMemoryFileMapper memoryFileMapper;

    // ─── 公开接口 ───────────────────────────────────────────────────────────

    /**
     * 查询记忆索引。
     * sessionId=null → 只返回 common；sessionId 不为 null → 返回 common + 该 session 记忆。
     */
    public List<MemoryIndexEntry> listIndex(String userId, String sessionId) {
        LambdaQueryWrapper<UserMemoryFile> wrapper = Wrappers.lambdaQuery(UserMemoryFile.class)
                .eq(UserMemoryFile::getUserId, userId);
        if (sessionId != null) {
            wrapper.and(w -> w.isNull(UserMemoryFile::getSessionId)
                              .or().eq(UserMemoryFile::getSessionId, sessionId));
        } else {
            wrapper.isNull(UserMemoryFile::getSessionId);
        }
        wrapper.orderByAsc(UserMemoryFile::getUpdatedAt);

        return memoryFileMapper.selectList(wrapper).stream()
                .map(f -> new MemoryIndexEntry(
                        f.getName(), f.getDescription(), f.getMemoryType(),
                        f.getFilePath(), f.getAgentType(), f.getUpdatedAt(),
                        f.getSeq(), f.getPhase(), f.getBlockChainHash()))
                .collect(Collectors.toList());
    }

    /**
     * 批量读取文件内容。
     * filePaths 是相对于 memory_root 的路径（来自 DB 的 file_path 字段）。
     * 文件不存在时跳过，不抛异常。
     */
    public Map<String, String> readFiles(String userId, List<String> filePaths) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String relativePath : filePaths) {
            Path resolved = resolveAndValidate(userId, relativePath);
            if (Files.exists(resolved)) {
                result.put(relativePath, Files.readString(resolved, StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    /**
     * 写入一条记忆：写文件 → upsert DB → 重建 MEMORY.md。
     * 向后兼容：phase 和 blockChainHash 可为 null。
     */
    public String writeMemory(String userId, String sessionId, String scope,
                              String memoryType, String name, String description,
                              String content, String agentType) throws IOException {
        return writeMemory(userId, sessionId, scope, memoryType, name, description, content, agentType, null, null);
    }

    /**
     * 写入一条记忆（增强版）：写文件 → upsert DB（含 seq/phase/chainHash）→ 重建 MEMORY.md。
     *
     * @param userId         用户ID
     * @param sessionId      NULL = common 跨会话记忆，否则为 agentThreadId
     * @param scope          "common" | "session"
     * @param memoryType     user / feedback / project / reference
     * @param name           记忆名称（用于生成文件名）
     * @param description    一句话摘要
     * @param content        记忆正文 Markdown
     * @param agentType      写入来源（main/distillation/memory）
     * @param phase          阶段标签："规划" | "执行" | null
     * @param blockChainHash block chain 哈希前6位，标识轮次；null 表示 common 记忆
     * @return 相对路径
     */
    public String writeMemory(String userId, String sessionId, String scope,
                              String memoryType, String name, String description,
                              String content, String agentType,
                              String phase, String blockChainHash) throws IOException {
        String fileName    = sanitizeFileName(name);
        String relativePath = buildRelativePath(userId, sessionId, scope, fileName);

        Path lockFile = Paths.get(memoryRoot).resolve(userId).resolve(".write.lock");
        try {
            FileLock fileLock = FileLockHelper.acquire(lockFile, 50, 100L);
            try {
                Path filePath = Paths.get(memoryRoot).resolve(relativePath).normalize();
                validatePath(userId, filePath);
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, buildMarkdownContent(name, description, memoryType, content),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                upsertRecord(userId, sessionId, relativePath, memoryType, name, description,
                        agentType, phase, blockChainHash);

                regenerateIndex(userId, sessionId, scope);

                log.info("MEMORY_WRITE userId={} scope={} name={} seq={} phase={} chain={}",
                        userId, scope, name, getLatestSeq(userId, sessionId), phase, blockChainHash);
                return relativePath;
            } finally {
                fileLock.release();
                fileLock.channel().close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("写记忆文件时被中断: " + relativePath, e);
        }
    }

    // ─── 私有实现 ───────────────────────────────────────────────────────────

    private void upsertRecord(String userId, String sessionId, String filePath,
                               String memoryType, String name, String description,
                               String agentType, String phase, String blockChainHash) {
        LambdaQueryWrapper<UserMemoryFile> wrapper = Wrappers.lambdaQuery(UserMemoryFile.class)
                .eq(UserMemoryFile::getUserId, userId)
                .eq(UserMemoryFile::getFilePath, filePath);
        UserMemoryFile existing = memoryFileMapper.selectOne(wrapper);
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            UserMemoryFile record = new UserMemoryFile();
            record.setUserId(userId);
            record.setSessionId(sessionId);
            record.setFilePath(filePath);
            record.setMemoryType(memoryType);
            record.setName(name);
            record.setDescription(description);
            record.setAgentType(agentType);
            record.setPhase(phase);
            record.setBlockChainHash(blockChainHash);
            record.setSeq(getNextSeq(userId, sessionId));
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            memoryFileMapper.insert(record);
        } else {
            existing.setMemoryType(memoryType);
            existing.setName(name);
            existing.setDescription(description);
            existing.setAgentType(agentType);
            existing.setPhase(phase);
            existing.setBlockChainHash(blockChainHash);
            existing.setUpdatedAt(now);
            // seq 不变，保持创建时分配的序号
            memoryFileMapper.updateById(existing);
        }
    }

    /**
     * 格式化索引行（不截断 description）
     */
    private static String formatIndexLine(UserMemoryFile r) {
        String fileName = Paths.get(r.getFilePath()).getFileName().toString();
        String desc = r.getDescription() != null ? r.getDescription() : "";
        int seq = r.getSeq() != null ? r.getSeq() : 0;
        return String.format("- #%d [%s](%s) — %s%n", seq, r.getName(), fileName, desc);
    }

    /**
     * 获取下一个 seq：common(sessionId=null) 按 userId 递增，session 按 sessionId 递增。
     */
    private int getNextSeq(String userId, String sessionId) {
        LambdaQueryWrapper<UserMemoryFile> wrapper = Wrappers.lambdaQuery(UserMemoryFile.class)
                .eq(UserMemoryFile::getUserId, userId)
                .ne(UserMemoryFile::getMemoryType, "index");
        if (sessionId != null) {
            wrapper.eq(UserMemoryFile::getSessionId, sessionId);
        } else {
            wrapper.isNull(UserMemoryFile::getSessionId);
        }
        wrapper.orderByDesc(UserMemoryFile::getSeq).last("LIMIT 1");
        UserMemoryFile latest = memoryFileMapper.selectOne(wrapper);
        return (latest != null && latest.getSeq() != null) ? latest.getSeq() + 1 : 1;
    }

    /**
     * 获取最新 seq（日志用）。
     */
    private int getLatestSeq(String userId, String sessionId) {
        return Math.max(1, getNextSeq(userId, sessionId) - 1);
    }

    private static final int    INDEX_MAX_ROWS         = 200;
    private static final int    INDEX_MAX_BYTES        = 25_000;
    private static final int    INDEX_MAX_LINE         = 150;
    private static final int    SESSION_PROFILE_MAX    = 30;
    private static final int    COMMON_PROFILE_MAX     = 50;

    private void regenerateIndex(String userId, String sessionId, String scope) throws IOException {
        LambdaQueryWrapper<UserMemoryFile> wrapper = Wrappers.lambdaQuery(UserMemoryFile.class)
                .eq(UserMemoryFile::getUserId, userId)
                .ne(UserMemoryFile::getMemoryType, "index");
        if ("common".equals(scope)) {
            wrapper.isNull(UserMemoryFile::getSessionId);
        } else {
            wrapper.eq(UserMemoryFile::getSessionId, sessionId);
        }
        wrapper.orderByAsc(UserMemoryFile::getSeq);

        List<UserMemoryFile> records = new ArrayList<>(memoryFileMapper.selectList(wrapper));

        // ── 清理：有 chainHash 的记忆超 INDEX_MAX_ROWS 条 → 按时间删最早的 chainHash 组 ──
        List<UserMemoryFile> chainedRecords = records.stream()
                .filter(r -> r.getBlockChainHash() != null).collect(Collectors.toList());
        if (chainedRecords.size() > INDEX_MAX_ROWS) {
            // 收集 chainHash 出现顺序（seq asc = 时间顺序）
            List<String> chainOrder = new ArrayList<>();
            for (UserMemoryFile r : chainedRecords) {
                String hash = r.getBlockChainHash();
                if (!chainOrder.contains(hash)) {
                    chainOrder.add(hash);
                }
            }
            List<UserMemoryFile> toDelete = new ArrayList<>();
            for (String hash : chainOrder) {
                if (chainedRecords.size() - toDelete.size() <= INDEX_MAX_ROWS) break;
                List<UserMemoryFile> group = chainedRecords.stream()
                        .filter(r -> hash.equals(r.getBlockChainHash())).toList();
                toDelete.addAll(group);
            }
            purgeMemoryFiles(toDelete);
            records.removeAll(toDelete);
        }

        // ── 清理：用户画像（chainHash=null）超阈值 → 按 seq 删最早的 ──
        int profileMax = "common".equals(scope) ? COMMON_PROFILE_MAX : SESSION_PROFILE_MAX;
        List<UserMemoryFile> profileRecords = records.stream()
                .filter(r -> r.getBlockChainHash() == null).collect(Collectors.toList());
        if (profileRecords.size() > profileMax) {
            List<UserMemoryFile> profileToDelete = new ArrayList<>(
                    profileRecords.subList(0, profileRecords.size() - profileMax));
            purgeMemoryFiles(profileToDelete);
            records.removeAll(profileToDelete);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Memory Index\n\n");
        sb.append("## 说明\n");
        sb.append("本文件是选品会话的记忆索引，按 chainHash 分组记录了所有 API 数据。\n\n");
        sb.append("### 结构\n");
        sb.append("- 按 **chainHash** 分组，每组代表一批相关的数据记录\n");
        sb.append("- 每个条目格式：`#seq [名称-chainHash](文件路径) — 描述`\n");
        sb.append("- **seq**：会话内递增序号，标识时间顺序\n");
        sb.append("- **chainHash**：积木链内容的 MD5 前6位，标识属于哪个版本的积木链\n\n");
        sb.append("### 阶段说明\n");
        sb.append("- 每个 `## chain:xxx` 分组是一批相关数据，可能包含规划预览和/或执行真实数据\n");
        sb.append("- 每条记忆自带 phase 标签（规划/执行），从描述可判断是预览还是真实数据\n\n");
        sb.append("### 如何使用\n");
        sb.append("- 用户询问预览/验证数据 → 查看带「规划」标签的条目\n");
        sb.append("- 用户询问真实执行数据 → 查看带「执行」标签的条目\n");
        sb.append("- 调用 query_memory 工具读取具体 md 文件获取完整内容\n\n");
        sb.append("---\n\n");

        // 按 chainHash 分组，null hash 归入用户画像
        // 收集所有不同的 chainHash（保持出现顺序）
        List<String> chainOrder = new ArrayList<>();
        for (UserMemoryFile r : records) {
            String hash = r.getBlockChainHash();
            if (hash != null && !chainOrder.contains(hash)) {
                chainOrder.add(hash);
            }
        }

        // 输出每个 chain 分组
        for (String hash : chainOrder) {
            List<UserMemoryFile> group = records.stream()
                    .filter(r -> hash.equals(r.getBlockChainHash())).toList();
            // 取该组最早的创建时间
            String createdAt = group.stream()
                    .map(UserMemoryFile::getCreatedAt)
                    .filter(java.util.Objects::nonNull)
                    .min(java.util.Comparator.naturalOrder())
                    .map(t -> t.toLocalDate() + " " + t.toLocalTime().toString().substring(0, 5))
                    .orElse("未知");
            sb.append(String.format("## chain:%s（创建于 %s）%n", hash, createdAt));
            for (UserMemoryFile r : group) {
                sb.append(formatIndexLine(r));
            }
            sb.append("\n");
        }

        // 用户画像：chainHash 为 null 的记录
        List<UserMemoryFile> commonRecords = records.stream()
                .filter(r -> r.getBlockChainHash() == null).toList();
        if (!commonRecords.isEmpty()) {
            sb.append("## 用户画像\n");
            for (UserMemoryFile r : commonRecords) {
                sb.append(formatIndexLine(r));
            }
            sb.append("\n");
        }

        if (sb.length() > INDEX_MAX_BYTES) {
            sb.setLength(INDEX_MAX_BYTES);
            sb.append("\n\n> WARNING: MEMORY.md 超过 25KB，已截断。\n");
        }

        String indexRelPath = buildIndexRelativePath(userId, sessionId, scope);
        Path indexPath = Paths.get(memoryRoot).resolve(indexRelPath).normalize();
        validatePath(userId, indexPath);
        Files.createDirectories(indexPath.getParent());
        Files.writeString(indexPath, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        upsertRecord(userId, sessionId, indexRelPath, "index", "MEMORY", "该 scope 的记忆索引文件", "system", null, null);
    }

    /**
     * 三层清理：DB 记录 + 磁盘 .md 文件。
     */
    private void purgeMemoryFiles(List<UserMemoryFile> toDelete) {
        if (toDelete.isEmpty()) return;
        // ① DB 删除
        List<String> ids = toDelete.stream().map(UserMemoryFile::getId).toList();
        memoryFileMapper.deleteBatchIds(ids);
        // ② 磁盘删除
        for (UserMemoryFile r : toDelete) {
            try {
                Path filePath = Paths.get(memoryRoot).resolve(r.getFilePath()).normalize();
                Files.deleteIfExists(filePath);
            } catch (Exception e) {
                log.warn("清理记忆文件失败: {}", r.getFilePath(), e);
            }
        }
        log.info("清理记忆文件 {} 条", toDelete.size());
    }

    /**
     * 查询指定 scope 的 MEMORY.md 相对路径（从 DB 读取，保证与磁盘文件一致）。
     */
    public String getIndexPath(String userId, String sessionId, String scope) {
        LambdaQueryWrapper<UserMemoryFile> wrapper = Wrappers.lambdaQuery(UserMemoryFile.class)
                .eq(UserMemoryFile::getUserId, userId)
                .eq(UserMemoryFile::getMemoryType, "index");
        if ("common".equals(scope)) {
            wrapper.isNull(UserMemoryFile::getSessionId);
        } else {
            wrapper.eq(UserMemoryFile::getSessionId, sessionId);
        }
        UserMemoryFile record = memoryFileMapper.selectOne(wrapper);
        return record != null ? record.getFilePath() : buildIndexRelativePath(userId, sessionId, scope);
    }

    private Path resolveAndValidate(String userId, String relativePath) {
        Path resolved = Paths.get(memoryRoot).resolve(relativePath).normalize();
        validatePath(userId, resolved);
        return resolved;
    }

    /** 强制文件路径在 userId 目录下，防止路径穿越攻击 */
    private void validatePath(String userId, Path path) {
        Path userRoot = Paths.get(memoryRoot).resolve(userId).normalize();
        if (!path.startsWith(userRoot)) {
            throw new IllegalArgumentException("非法文件路径（路径穿越检测）: " + path);
        }
    }

    private String buildRelativePath(String userId, String sessionId, String scope, String fileName) {
        if ("common".equals(scope)) {
            return userId + "/common/" + fileName;
        }
        return userId + "/select_product/" + sessionId + "/memory/" + fileName;
    }

    private String buildIndexRelativePath(String userId, String sessionId, String scope) {
        if ("common".equals(scope)) {
            return userId + "/common/MEMORY.md";
        }
        return userId + "/select_product/" + sessionId + "/memory/MEMORY.md";
    }

    /** 中英文均支持，特殊字符替换为下划线 */
    private String sanitizeFileName(String name) {
        String sanitized = name.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5_\\-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (sanitized.isEmpty()) {
            sanitized = "memory_" + System.currentTimeMillis();
        }
        return sanitized + ".md";
    }

    private String buildMarkdownContent(String name, String description, String memoryType, String content) {
        return "---\n"
                + "name: " + name + "\n"
                + "description: " + (description != null ? description : "") + "\n"
                + "type: " + memoryType + "\n"
                + "---\n\n"
                + content;
    }

    // ─── DTO ────────────────────────────────────────────────────────────────

    public record MemoryIndexEntry(
            String name,
            String description,
            String memoryType,
            String filePath,
            String agentType,
            LocalDateTime updatedAt,
            Integer seq,
            String phase,
            String blockChainHash
    ) {}
}
