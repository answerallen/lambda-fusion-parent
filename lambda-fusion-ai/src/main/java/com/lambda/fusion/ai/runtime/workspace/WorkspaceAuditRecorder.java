package com.lambda.fusion.ai.runtime.workspace;

import com.lambda.fusion.ai.AiConstants.AppType;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.runtime.workspace.entity.WorkspaceAuditEntity;
import com.lambda.fusion.ai.runtime.workspace.service.WorkspaceAuditService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自演化审计记录器：selfEvolve 应用每轮对话后扫描 workspace 中被修改文件，复制快照写入审计表。
 * 通过文件 mtime &gt; 轮次起始时间检测变更（无需侵入 AgentScope 工具调用链）；.audit 目录排除扫描。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class WorkspaceAuditRecorder {

    private static final String AUDIT_DIR_NAME = ".audit";
    private static final String AGENTSCOPE_DIR_NAME = ".agentscope";
    private static final String INDEX_DIR_NAME = ".index";

    private final AppService appService;
    private final WorkspaceFileService workspaceFileService;
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
            List<String> changed = scanChanged(session.getTenantId(), app, turnStartMillis);
            for (String relPath : changed) {
                String snapshotRel = copySnapshot(session.getTenantId(), app, relPath, turnStartMillis);
                WorkspaceAuditEntity entry = new WorkspaceAuditEntity();
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

    private List<String> scanChanged(String tenantId, AppEntity app, long turnStartMillis) throws IOException {
        return workspaceFileService.list(tenantId, app).stream()
                .filter(entry -> !entry.directory())
                .filter(entry -> !isInternal(entry.path()))
                .filter(entry -> entry.updatedAt() > turnStartMillis)
                .map(WorkspaceFileEntry::path)
                .toList();
    }

    private String copySnapshot(String tenantId, AppEntity app, String relPath, long turnStartMillis) {
        String snapshotPath = AUDIT_DIR_NAME + "/" + turnStartMillis + "/" + relPath;
        try {
            String content = workspaceFileService.read(tenantId, app, relPath);
            workspaceFileService.write(tenantId, app, snapshotPath, content);
            return snapshotPath;
        } catch (Exception e) {
            log.warn("快照复制失败: {}", relPath, e);
            return null;
        }
    }

    private boolean isInternal(String path) {
        return path.equals(AUDIT_DIR_NAME)
                || path.startsWith(AUDIT_DIR_NAME + "/")
                || path.equals(AGENTSCOPE_DIR_NAME)
                || path.startsWith(AGENTSCOPE_DIR_NAME + "/")
                || path.equals(INDEX_DIR_NAME)
                || path.startsWith(INDEX_DIR_NAME + "/");
    }
}
