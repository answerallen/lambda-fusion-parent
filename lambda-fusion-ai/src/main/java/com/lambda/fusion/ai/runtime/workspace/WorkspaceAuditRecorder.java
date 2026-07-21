package com.lambda.fusion.ai.runtime.workspace;

import com.lambda.fusion.ai.AiConstants.AppType;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.runtime.workspace.entity.WorkspaceAuditEntity;
import com.lambda.fusion.ai.runtime.workspace.service.WorkspaceAuditService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自演化审计记录器：在 selfEvolve 应用的每轮对话后，扫描 workspace 中在本轮被修改的文件，
 * 复制快照并写入审计表。
 *
 * <p>通过文件 mtime &gt; 轮次起始时间检测变更（无需侵入 AgentScope 工具调用链）。
 * .audit 目录本身排除扫描。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class WorkspaceAuditRecorder {

    private static final int MAX_DEPTH = 5;
    private static final String AUDIT_DIR_NAME = ".audit";

    private final AppService appService;
    private final WorkspacePaths workspacePaths;
    private final WorkspaceAuditService workspaceAuditService;

    /**
     * 记录本轮对话期间 workspace 的变更。仅 selfEvolve WORKSPACE 应用生效；非自演化应用直接返回。
     */
    public void recordChanges(ChatSessionEntity session, long turnStartMillis) {
        try {
            AppEntity app = appService.loadById(session.getAppId());
            if (!AppType.WORKSPACE.getCode().equalsIgnoreCase(app.getAppType())
                    || !Boolean.TRUE.equals(app.getSelfEvolve())) {
                return;
            }
            Path workspace = workspacePaths.resolveAppWorkspace(session.getTenantId(), app.getId());
            if (!Files.exists(workspace)) {
                return;
            }
            Path auditDir = workspace.resolve(AUDIT_DIR_NAME).resolve(String.valueOf(turnStartMillis));
            List<String> changed = scanChanged(workspace, turnStartMillis);
            for (String relPath : changed) {
                String snapshotRel = copySnapshot(workspace, relPath, auditDir);
                WorkspaceAuditEntity entry = new WorkspaceAuditEntity();
                entry.setTenantId(session.getTenantId());
                entry.setAppId(app.getId());
                entry.setSessionId(session.getId());
                entry.setFilePath(relPath);
                entry.setOperation("MODIFIED");
                entry.setSnapshotPath(snapshotRel);
                entry.setOperator("agent");
                entry.setCreatedAt(LocalDateTime.now());
                workspaceAuditService.record(entry);
            }
            if (!changed.isEmpty()) {
                log.info("自演化审计: app={}, 变更 {} 个文件", app.getId(), changed.size());
            }
        } catch (Exception e) {
            log.error("自演化审计记录失败: session={}", session.getId(), e);
        }
    }

    private List<String> scanChanged(Path workspace, long turnStartMillis) throws IOException {
        List<String> changed = new ArrayList<>();
        Path auditRoot = workspace.resolve(AUDIT_DIR_NAME).toAbsolutePath().normalize();
        Path base = workspace.toAbsolutePath().normalize();
        try (Stream<Path> stream = Files.walk(base, MAX_DEPTH)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toAbsolutePath().normalize().startsWith(auditRoot))
                    .forEach(p -> {
                        try {
                            long mtime = Files.getLastModifiedTime(p).toMillis();
                            if (mtime > turnStartMillis) {
                                Path rel = base.relativize(p.toAbsolutePath().normalize());
                                changed.add(rel.toString().replace('\\', '/'));
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }
        return changed;
    }

    private String copySnapshot(Path workspace, String relPath, Path auditDir) {
        try {
            Path src = workspace.resolve(relPath).toAbsolutePath().normalize();
            Path dst = auditDir.resolve(relPath).toAbsolutePath().normalize();
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return workspace
                    .toAbsolutePath()
                    .normalize()
                    .relativize(dst)
                    .toString()
                    .replace('\\', '/');
        } catch (IOException e) {
            log.warn("快照复制失败: {}", relPath, e);
            return null;
        }
    }
}
