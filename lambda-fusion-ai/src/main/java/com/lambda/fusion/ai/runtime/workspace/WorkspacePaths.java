package com.lambda.fusion.ai.runtime.workspace;

import com.lambda.fusion.ai.AiConstants.WorkspaceStorageType;
import com.lambda.fusion.ai.AiProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/**
 * Workspace 路径解析。LOCAL 使用原应用目录；远程存储使用独立的节点初始化模板目录，防止切换配置时误读原本地数据。
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
        WorkspaceStorageType type =
                WorkspaceStorageType.of(aiProperties.getWorkspace().getStorage().getType());
        return resolveAppWorkspace(tenantId, appId, type == null ? WorkspaceStorageType.LOCAL : type);
    }

    public Path resolveAppWorkspace(String tenantId, String appId, WorkspaceStorageType storageType) {
        return resolveStorageRoot(storageType)
                .resolve("tenants")
                .resolve(Objects.toString(tenantId, "_default"))
                .resolve("apps")
                .resolve(appId);
    }

    /**
     * 删除当前存储类型在本节点上的所有应用目录（best-effort，删除应用时调用）。远程 BaseStore 不在此处扫描或删除。
     *
     * @param appId 应用ID
     */
    public void deleteAppWorkspaces(String appId) {
        WorkspaceStorageType type =
                WorkspaceStorageType.of(aiProperties.getWorkspace().getStorage().getType());
        Path tenantsDir = resolveStorageRoot(type == null ? WorkspaceStorageType.LOCAL : type)
                .resolve("tenants");
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
                public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc)
                        throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("递归删除 {} 失败: {}", path, e.getMessage());
        }
    }

    private Path resolveStorageRoot(WorkspaceStorageType storageType) {
        String root = StringUtils.defaultIfBlank(aiProperties.getWorkspace().getRoot(), DEFAULT_ROOT);
        Path workspaceRoot = Paths.get(root);
        if (storageType == WorkspaceStorageType.LOCAL) {
            return workspaceRoot;
        }
        return workspaceRoot
                .resolve(".remote-templates")
                .resolve(storageType.name().toLowerCase(Locale.ROOT));
    }
}
