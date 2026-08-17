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
 * 构建应用级远程 Workspace 文件系统。
 *
 * <p>路由与 AgentScope {@code RemoteFilesystemSpec} 的 AGENT 隔离模式一致，但把未路由文件的本地层
 * 严格锚定在 Workspace。AgentScope 2.0 原实现的默认本地层为 unrestricted，根目录 ls/glob 会落到宿主机
 * 文件系统根目录，因此不能直接用于服务端多租户场景。
 */
final class WorkspaceRemoteFilesystemFactory {

    private WorkspaceRemoteFilesystemFactory() {}

    static AbstractFilesystem create(BaseStore store, Path workspace, String agentId) {
        LocalFilesystem local = new LocalFilesystem(workspace, true, 10, null);
        LocalFilesystem rootTemplate = new LocalFilesystem(workspace, true, 10, null);
        Map<String, AbstractFilesystem> routes = new LinkedHashMap<>();
        routes.put("AGENTS.md", exactFile(store, agentId, "root", rootTemplate));
        routes.put("MEMORY.md", exactFile(store, agentId, "root", rootTemplate));
        routes.put("tools.json", exactFile(store, agentId, "root", rootTemplate));
        routes.put("memory/", directory(store, agentId, workspace.resolve("memory"), "memory"));
        routes.put("skills/", directory(store, agentId, workspace.resolve("skills"), "skills"));
        routes.put("subagents/", directory(store, agentId, workspace.resolve("subagents"), "subagents"));
        routes.put("knowledge/", directory(store, agentId, workspace.resolve("knowledge"), "knowledge"));
        routes.put("plans/", directory(store, agentId, workspace.resolve("plans"), "plans"));
        routes.put(".audit/", directory(store, agentId, workspace.resolve(".audit"), ".audit"));
        routes.put(".agentscope/", directory(store, agentId, workspace.resolve(".agentscope"), ".agentscope"));
        routes.put(
                "agents/" + agentId + "/sessions/",
                directory(
                        store,
                        agentId,
                        workspace.resolve("agents").resolve(agentId).resolve("sessions"),
                        "sessions"));
        routes.put(
                "agents/" + agentId + "/tasks/",
                directory(
                        store,
                        agentId,
                        workspace.resolve("agents").resolve(agentId).resolve("tasks"),
                        "tasks"));
        return new CompositeFilesystem(local, routes);
    }

    private static AbstractFilesystem exactFile(
            BaseStore store, String agentId, String segment, LocalFilesystem template) {
        return new OverlayFilesystem(remote(store, agentId, segment), template);
    }

    private static AbstractFilesystem directory(
            BaseStore store, String agentId, Path templateDirectory, String segment) {
        LocalFilesystem template = new LocalFilesystem(templateDirectory, true, 10, null);
        return new OverlayFilesystem(remote(store, agentId, segment), template);
    }

    private static AbstractFilesystem remote(BaseStore store, String agentId, String segment) {
        return new RemoteFilesystem(store, List.of("agents", agentId, "shared", segment));
    }
}
