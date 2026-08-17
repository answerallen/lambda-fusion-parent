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
        boolean changed = createDirectoriesIfAbsent(workspace);
        changed |= writeIfAbsent(
                workspace.resolve("AGENTS.md"),
                "# " + Objects.toString(app.getName(), "Agent") + "\n\n"
                        + "在此维护该应用的人格与指令。系统提示词（DB）作为基线，本文件作为可演化的补充。\n");
        changed |= createDirectoriesIfAbsent(workspace.resolve("skills"));
        changed |= createDirectoriesIfAbsent(workspace.resolve("subagents"));
        changed |= createDirectoriesIfAbsent(workspace.resolve("memory"));
        changed |= createDirectoriesIfAbsent(workspace.resolve("knowledge"));
        changed |= writeIfAbsent(workspace.resolve("tools.json"), DEFAULT_TOOLS_JSON);
        if (changed) {
            log.info("workspace 脚手架完成: app={}, path={}", app.getId(), workspace);
        }
    }

    private boolean createDirectoriesIfAbsent(Path directory) throws IOException {
        boolean absent = Files.notExists(directory);
        Files.createDirectories(directory);
        return absent;
    }

    private boolean writeIfAbsent(Path file, String content) throws IOException {
        if (!Files.exists(file)) {
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return true;
        }
        return false;
    }
}
