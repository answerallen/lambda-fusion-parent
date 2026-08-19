package com.lambda.fusion.ai.runtime.workspace;

import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.filesystem.CompositeFilesystem;
import io.agentscope.harness.agent.filesystem.OverlayFilesystem;
import io.agentscope.harness.agent.filesystem.local.LocalFilesystem;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建远程 Workspace 文件系统：应用指令/技能/知识等文件由同一 Agent 共享，
 * {@code MEMORY.md} 与 {@code memory/} 按 AgentScope USER 语义隔离；
 * 未路由文件的本地层严格锚定 Workspace，避免越出应用目录。
 */
final class WorkspaceRemoteFilesystemFactory {

    private static final String DEFAULT_USER_ID = "_default";

    private WorkspaceRemoteFilesystemFactory() {}

    static AbstractFilesystem create(BaseStore store, Path workspace, String agentId) {
        LocalFilesystem local = new LocalFilesystem(workspace, true, 10, null);
        LocalFilesystem rootTemplate = new LocalFilesystem(workspace, true, 10, null);
        Map<String, AbstractFilesystem> routes = new LinkedHashMap<>();
        routes.put("AGENTS.md", exactFile(sharedRemote(store, agentId, "root"), rootTemplate));
        routes.put("MEMORY.md", exactFile(userRemote(store, agentId, "root"), rootTemplate));
        routes.put("tools.json", exactFile(sharedRemote(store, agentId, "root"), rootTemplate));
        routes.put("memory/", directory(userRemote(store, agentId, "memory"), workspace.resolve("memory")));
        routes.put("skills/", directory(sharedRemote(store, agentId, "skills"), workspace.resolve("skills")));
        routes.put("subagents/", directory(sharedRemote(store, agentId, "subagents"), workspace.resolve("subagents")));
        routes.put("knowledge/", directory(sharedRemote(store, agentId, "knowledge"), workspace.resolve("knowledge")));
        routes.put("plans/", directory(sharedRemote(store, agentId, "plans"), workspace.resolve("plans")));
        routes.put(".audit/", directory(sharedRemote(store, agentId, ".audit"), workspace.resolve(".audit")));
        routes.put(
                ".agentscope/",
                directory(sharedRemote(store, agentId, ".agentscope"), workspace.resolve(".agentscope")));
        routes.put(
                "agents/" + agentId + "/sessions/",
                directory(
                        sharedRemote(store, agentId, "sessions"),
                        workspace.resolve("agents").resolve(agentId).resolve("sessions")));
        routes.put(
                "agents/" + agentId + "/tasks/",
                directory(
                        sharedRemote(store, agentId, "tasks"),
                        workspace.resolve("agents").resolve(agentId).resolve("tasks")));
        return new CompositeFilesystem(local, routes);
    }

    private static AbstractFilesystem exactFile(AbstractFilesystem remote, LocalFilesystem template) {
        return new OverlayFilesystem(remote, template);
    }

    private static AbstractFilesystem directory(AbstractFilesystem remote, Path templateDirectory) {
        LocalFilesystem template = new LocalFilesystem(templateDirectory, true, 10, null);
        return new OverlayFilesystem(remote, template);
    }

    private static AbstractFilesystem sharedRemote(BaseStore store, String agentId, String segment) {
        return new RemoteFilesystem(store, List.of("agents", agentId, "shared", segment));
    }

    private static AbstractFilesystem userRemote(BaseStore store, String agentId, String segment) {
        return new RemoteFilesystem(store, context -> {
            String userId = context == null ? null : context.getUserId();
            String effectiveUserId = userId == null || userId.isBlank() ? DEFAULT_USER_ID : userId;
            return List.of("agents", agentId, "users", effectiveUserId, segment);
        });
    }
}
