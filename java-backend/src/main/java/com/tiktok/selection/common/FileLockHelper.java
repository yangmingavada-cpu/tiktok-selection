package com.tiktok.selection.common;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 基于 Java NIO FileChannel 的 OS 级文件锁工具。
 *
 * <p>与 JVM 内 ReentrantLock 的核心区别：
 * <ul>
 *   <li>锁文件存于磁盘（同挂载卷），跨 Docker 实例互斥</li>
 *   <li>进程崩溃后 OS 自动释放，不会产生死锁孤锁</li>
 *   <li>调用方持有 FileLock 后须在 finally 中 release() + channel().close()</li>
 * </ul>
 *
 * <p>参考：Claude Code Agent Teams consolidation lock 模式
 */
public final class FileLockHelper {

    private FileLockHelper() {}

    /**
     * 尝试获取文件排他锁，最多重试 {@code maxRetries} 次，每次等待 {@code intervalMs} 毫秒。
     *
     * @param lockFile  锁文件路径（自动创建，不存在时新建）
     * @param maxRetries 最大重试次数（建议 50）
     * @param intervalMs 每次重试间隔毫秒（建议 100ms → 最多等 5 秒）
     * @return 已获取的 {@link FileLock}，调用方负责 release() + channel().close()
     * @throws IOException          磁盘 IO 失败
     * @throws BusinessException    超过最大重试次数仍未获锁
     * @throws InterruptedException 等待期间线程被中断
     */
    public static FileLock acquire(Path lockFile, int maxRetries, long intervalMs)
            throws IOException, InterruptedException {

        Files.createDirectories(lockFile.getParent());

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock != null) {
                return lock;
            }
            channel.close();
            Thread.sleep(intervalMs);
        }

        throw new BusinessException(
                ErrorCode.SYSTEM_ERROR,
                "获取文件写锁超时（已重试 " + maxRetries + " 次）: " + lockFile);
    }
}
