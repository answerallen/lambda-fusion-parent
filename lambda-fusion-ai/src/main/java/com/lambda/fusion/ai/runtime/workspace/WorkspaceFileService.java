package com.lambda.fusion.ai.runtime.workspace;

import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Workspace 文件读写服务。供管理 API 与自演化审计使用。所有路径做越界校验。
 *
 * @author Jin
 */
@Slf4j
@Component
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class WorkspaceFileService {

    private static final int MAX_LIST_DEPTH = 5;

    public List<WorkspaceFileEntry> list(Path workspace) throws IOException {
        List<WorkspaceFileEntry> entries = new ArrayList<>();
        if (!Files.exists(workspace)) {
            return entries;
        }
        Path base = workspace.toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.walk(base, MAX_LIST_DEPTH)) {
            stream.filter(p -> !p.equals(base)).forEach(p -> {
                Path rel = base.relativize(p.toAbsolutePath().normalize());
                try {
                    entries.add(new WorkspaceFileEntry(
                            p.getFileName().toString(),
                            rel.toString().replace('\\', '/'),
                            Files.isDirectory(p),
                            Files.isDirectory(p) ? 0L : Files.size(p),
                            Files.getLastModifiedTime(p).toMillis()));
                } catch (IOException e) {
                    log.warn("读取 workspace 文件元数据失败: {}", p, e);
                }
            });
        }
        return entries;
    }

    public String read(Path workspace, String relativePath) throws IOException {
        Path resolved = resolveSafe(workspace, relativePath);
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "文件不存在: " + relativePath);
        }
        return Files.readString(resolved, StandardCharsets.UTF_8);
    }

    public void write(Path workspace, String relativePath, String content) throws IOException {
        Path resolved = resolveSafe(workspace, relativePath);
        if (resolved.getParent() != null) {
            Files.createDirectories(resolved.getParent());
        }
        Files.writeString(resolved, content, StandardCharsets.UTF_8);
    }

    private Path resolveSafe(Path workspace, String relativePath) {
        Path base = workspace.toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new AiBusinessException(AiErrorCode.OPERATION_NOT_SUPPORTED, "非法路径: " + relativePath);
        }
        return resolved;
    }
}
