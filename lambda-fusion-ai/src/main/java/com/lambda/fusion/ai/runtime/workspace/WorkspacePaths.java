package com.lambda.fusion.ai.runtime.workspace;

import com.lambda.fusion.ai.AiProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Workspace 路径解析。每个 WORKSPACE 型应用对应一个目录：
 * {@code {workspace.root}/tenants/{tenantId}/apps/{appId}/}。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class WorkspacePaths {

    private static final String DEFAULT_ROOT = System.getProperty("user.home") + "/.agentscope/fusion";

    private final AiProperties aiProperties;

    public Path resolveAppWorkspace(String tenantId, String appId) {
        String root = StringUtils.defaultIfBlank(aiProperties.getWorkspace().getRoot(), DEFAULT_ROOT);
        return Paths.get(root, "tenants", Objects.toString(tenantId, "_default"), "apps", appId);
    }

    /**
     * 删除所有租户下该应用的 workspace 目录（best-effort，删除应用时调用）。
     *
     * @param appId 应用ID
     */
    public void deleteAppWorkspaces(String appId) {
        String root = StringUtils.defaultIfBlank(aiProperties.getWorkspace().getRoot(), DEFAULT_ROOT);
        Path tenantsDir = Paths.get(root, "tenants");
        if (!Files.exists(tenantsDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(tenantsDir)) {
            stream.filter(Files::isDirectory).forEach(tenantDir -> {
                Path appWorkspace = tenantDir.resolve("apps").resolve(appId);
                if (Files.exists(appWorkspace)) {
                    deleteRecursively(appWorkspace);
                }
            });
        } catch (IOException e) {
            log.warn("清理应用 {} 的 workspace 失败: {}", appId, e.getMessage());
        }
    }

    private void deleteRecursively(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("递归删除 {} 失败: {}", path, e.getMessage());
        }
    }
}
