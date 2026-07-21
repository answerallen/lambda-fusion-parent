package com.lambda.fusion.ai.runtime.workspace;

import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Workspace 脚手架：WORKSPACE 型应用创建时初始化目录结构与默认文件。
 *
 * <p>生成：{@code AGENTS.md}（人格占位）、{@code skills/}、{@code subagents/}、{@code memory/}、
 * {@code knowledge/}、{@code tools.json}（空 allow/deny/mcpServers）。
 *
 * @author Jin
 */
@Slf4j
@Component
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class WorkspaceScaffolder {

    private static final String DEFAULT_TOOLS_JSON = "{\"allow\":[],\"deny\":[],\"mcpServers\":{}}";

    /**
     * 脚手架 workspace。已存在的文件不覆盖。
     */
    public void scaffold(Path workspace, AppEntity app) throws IOException {
        Files.createDirectories(workspace);
        writeIfAbsent(
                workspace.resolve("AGENTS.md"),
                "# " + Objects.toString(app.getName(), "Agent") + "\n\n"
                        + "在此维护该应用的人格与指令。系统提示词（DB）作为基线，本文件作为可演化的补充。\n");
        Files.createDirectories(workspace.resolve("skills"));
        Files.createDirectories(workspace.resolve("subagents"));
        Files.createDirectories(workspace.resolve("memory"));
        Files.createDirectories(workspace.resolve("knowledge"));
        writeIfAbsent(workspace.resolve("tools.json"), DEFAULT_TOOLS_JSON);
        log.info("workspace 脚手架完成: app={}, path={}", app.getId(), workspace);
    }

    private void writeIfAbsent(Path file, String content) throws IOException {
        if (!Files.exists(file)) {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }
    }
}
