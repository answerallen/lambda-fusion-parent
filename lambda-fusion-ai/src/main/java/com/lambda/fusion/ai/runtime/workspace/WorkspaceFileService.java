package com.lambda.fusion.ai.runtime.workspace;

import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.model.FileInfo;
import io.agentscope.harness.agent.filesystem.model.FileUploadResponse;
import io.agentscope.harness.agent.filesystem.model.LsResult;
import io.agentscope.harness.agent.filesystem.model.ReadResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/**
 * Workspace 文件读写服务。管理 API 与自演化审计统一服从系统级存储配置。
 *
 * <p>LOCAL 保留原生 NIO 行为；分布式模式通过 AgentScope 远程文件系统访问共享存储，避免请求落到不同节点时
 * 读取各自本地目录。
 *
 * @author Jin
 */
@Component
@RequiredArgsConstructor
public class WorkspaceFileService {

    private static final int MAX_LIST_DEPTH = 5;
    private static final RuntimeContext EMPTY_CONTEXT = RuntimeContext.empty();

    private final WorkspaceStorage workspaceStorage;

    public List<WorkspaceFileEntry> list(String tenantId, AppEntity app) throws IOException {
        Path workspace = workspaceStorage.initializeWorkspace(tenantId, app);
        if (!workspaceStorage.isDistributed()) {
            return listLocal(workspace);
        }
        AbstractFilesystem filesystem = workspaceStorage.openDistributedFilesystem(tenantId, app, workspace);
        return listRemote(filesystem);
    }

    public String read(String tenantId, AppEntity app, String relativePath) throws IOException {
        String path = validateRelativePath(relativePath);
        Path workspace = workspaceStorage.initializeWorkspace(tenantId, app);
        if (!workspaceStorage.isDistributed()) {
            Path resolved = resolveSafe(workspace, path);
            if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
                throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "文件不存在: " + relativePath);
            }
            return Files.readString(resolved, StandardCharsets.UTF_8);
        }

        ReadResult result = workspaceStorage
                .openDistributedFilesystem(tenantId, app, workspace)
                .read(EMPTY_CONTEXT, path, 0, 0);
        if (!result.isSuccess() || result.fileData() == null) {
            throw new AiBusinessException(
                    AiErrorCode.OPERATION_NOT_SUPPORTED,
                    result.error() == null ? "文件不存在: " + relativePath : result.error());
        }
        return result.fileData().content();
    }

    public void write(String tenantId, AppEntity app, String relativePath, String content) throws IOException {
        String path = validateRelativePath(relativePath);
        Path workspace = workspaceStorage.initializeWorkspace(tenantId, app);
        if (!workspaceStorage.isDistributed()) {
            Path resolved = resolveSafe(workspace, path);
            if (resolved.getParent() != null) {
                Files.createDirectories(resolved.getParent());
            }
            Files.writeString(resolved, content, StandardCharsets.UTF_8);
            return;
        }

        AbstractFilesystem filesystem = workspaceStorage.openDistributedFilesystem(tenantId, app, workspace);
        List<FileUploadResponse> responses =
                workspaceStorage.withWriteLock(tenantId, app, () -> upload(filesystem, path, content));
        if (responses.isEmpty() || !responses.getFirst().isSuccess()) {
            String error = responses.isEmpty()
                    ? "Workspace 文件写入无响应"
                    : responses.getFirst().error();
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, error);
        }
    }

    private List<FileUploadResponse> upload(AbstractFilesystem filesystem, String path, String content) {
        return filesystem.uploadFiles(
                EMPTY_CONTEXT, List.of(Map.entry(path, content.getBytes(StandardCharsets.UTF_8))));
    }

    private List<WorkspaceFileEntry> listLocal(Path workspace) throws IOException {
        List<WorkspaceFileEntry> entries = new ArrayList<>();
        Path base = workspace.toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.walk(base, MAX_LIST_DEPTH)) {
            stream.filter(path -> !path.equals(base)).forEach(path -> {
                Path relative = base.relativize(path.toAbsolutePath().normalize());
                String relativePath = relative.toString().replace('\\', '/');
                if (isSystemInternal(relativePath)) {
                    return;
                }
                try {
                    entries.add(new WorkspaceFileEntry(
                            path.getFileName().toString(),
                            relativePath,
                            Files.isDirectory(path),
                            Files.isDirectory(path) ? 0L : Files.size(path),
                            Files.getLastModifiedTime(path).toMillis()));
                } catch (IOException ignored) {
                    // 文件可能在遍历过程中被 Agent 删除；下一次列表请求会反映最终状态。
                }
            });
        }
        return entries;
    }

    private List<WorkspaceFileEntry> listRemote(AbstractFilesystem filesystem) throws IOException {
        Map<String, WorkspaceFileEntry> entries = new LinkedHashMap<>();
        Queue<DirectoryDepth> pending = new ArrayDeque<>();
        pending.add(new DirectoryDepth(".", 0));

        while (!pending.isEmpty()) {
            DirectoryDepth current = pending.remove();
            LsResult result = filesystem.ls(EMPTY_CONTEXT, current.path());
            assertSuccess(result);
            if (result.entries() == null) {
                continue;
            }
            for (FileInfo fileInfo : result.entries()) {
                String path = normalizePath(fileInfo.path());
                if (path.isEmpty() || path.equals(".") || isSystemInternal(path)) {
                    continue;
                }
                WorkspaceFileEntry previous = entries.put(path, toEntry(path, fileInfo, filesystem));
                if (previous == null && fileInfo.isDirectory() && current.depth() < MAX_LIST_DEPTH - 1) {
                    pending.add(new DirectoryDepth(path, current.depth() + 1));
                }
            }
        }
        return entries.values().stream()
                .sorted(Comparator.comparing(WorkspaceFileEntry::path))
                .toList();
    }

    private WorkspaceFileEntry toEntry(String path, FileInfo fileInfo, AbstractFilesystem filesystem) {
        String name = path;
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            name = path.substring(slash + 1);
        }
        long size = fileInfo.isDirectory() ? 0L : fileInfo.size();
        long updatedAt = parseUpdatedAt(fileInfo.modifiedAt());
        if (!fileInfo.isDirectory() && (size == 0L || updatedAt == 0L)) {
            ReadResult read = filesystem.read(EMPTY_CONTEXT, path, 0, 0);
            if (read.isSuccess() && read.fileData() != null) {
                if (read.fileData().content() != null) {
                    size = read.fileData().content().getBytes(StandardCharsets.UTF_8).length;
                }
                long contentUpdatedAt = parseUpdatedAt(read.fileData().modifiedAt());
                if (contentUpdatedAt > 0L) {
                    updatedAt = contentUpdatedAt;
                }
            }
        }
        return new WorkspaceFileEntry(name, path, fileInfo.isDirectory(), size, updatedAt);
    }

    private long parseUpdatedAt(String modifiedAt) {
        if (modifiedAt == null || modifiedAt.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(modifiedAt).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String validateRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "非法路径: " + relativePath);
        }
        String candidate = getCandidate(relativePath);
        String normalized = normalizePath(candidate);
        if (normalized.isEmpty() || normalized.equals(".") || isSystemInternal(normalized)) {
            throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "非法路径: " + relativePath);
        }
        return normalized;
    }

    private static @NonNull String getCandidate(String relativePath) {
        String candidate = relativePath.replace('\\', '/').strip();
        try {
            if (candidate.startsWith("/") || Path.of(candidate).isAbsolute()) {
                throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "非法路径: " + relativePath);
            }
        } catch (InvalidPathException e) {
            throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "非法路径: " + relativePath);
        }
        for (String segment : candidate.split("/")) {
            if (segment.equals("..")) {
                throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "非法路径: " + relativePath);
            }
        }
        return candidate;
    }

    private Path resolveSafe(Path workspace, String relativePath) {
        Path base = workspace.toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "非法路径: " + relativePath);
        }
        return resolved;
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        for (String segment : path.replace('\\', '/').strip().split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (!normalized.isEmpty()) {
                normalized.append('/');
            }
            normalized.append(segment);
        }
        return normalized.toString();
    }

    private void assertSuccess(LsResult result) throws IOException {
        if (!result.isSuccess()) {
            throw new IOException(result.error() == null ? "Workspace 文件列表读取失败" : result.error());
        }
    }

    private boolean isSystemInternal(String path) {
        return path.equals(".agentscope")
                || path.startsWith(".agentscope/")
                || path.equals(".index")
                || path.startsWith(".index/");
    }

    private record DirectoryDepth(String path, int depth) {}
}
