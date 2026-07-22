package com.lambda.fusion.ai.runtime;

import com.lambda.fusion.ai.AiConstants.AppType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.event.AiConfigChangedEvent;
import com.lambda.fusion.ai.runtime.sandbox.SandboxSpecResolver;
import com.lambda.fusion.ai.runtime.workspace.WorkspacePaths;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceScaffolder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 智能应用运行时工厂：按 {@code (appId, tenantId)} 构建并缓存 {@link HarnessAgent}。
 *
 * <p>支持两种应用类型：
 * <ul>
 *   <li>{@code CHAT}：纯 DB 配置，无 workspace，关闭 harness workspace 能力（v1 行为）。</li>
 *   <li>{@code WORKSPACE}：per-app workspace，对齐 AgentScope harness 完整能力（AGENTS.md/技能/子agent/记忆）。
 *       {@code selfEvolve=false}（ASSISTANT）只读、无自记忆；{@code selfEvolve=true}（AUTONOMOUS）可写、自演化。
 *       {@code sandboxBackend} 非 HOST 时走沙箱文件系统（Docker/K8s/E2B/Daytona/AgentRun，条件装配），
 *       启用 shell 工具与执行隔离；后端不可用时回退宿主文件系统。</li>
 * </ul>
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class AiAgentFactory {

    private final AppService appService;
    private final AiModelResolver aiModelResolver;
    private final ToolkitAssembler toolkitAssembler;
    private final AiProperties aiProperties;
    private final WorkspacePaths workspacePaths;
    private final WorkspaceScaffolder workspaceScaffolder;
    private final SandboxSpecResolver sandboxSpecResolver;

    private final Map<String, HarnessAgent> cache = new ConcurrentHashMap<>();

    public HarnessAgent getOrBuild(String appId, String tenantId) {
        return cache.computeIfAbsent(cacheKey(appId, tenantId), k -> build(appId, tenantId));
    }

    public void invalidateApp(String appId) {
        String prefix = appId + "|";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void invalidateAll() {
        cache.clear();
    }

    @EventListener
    public void onConfigChanged(AiConfigChangedEvent event) {
        if (event.appId() == null) {
            log.info("AI 配置变更，失效全部 Agent 缓存");
            invalidateAll();
        } else {
            log.info("应用 {} 配置变更，失效对应 Agent 缓存", event.appId());
            invalidateApp(event.appId());
        }
    }

    private HarnessAgent build(String appId, String tenantId) {
        AppEntity app = appService.loadById(appId);
        if (Boolean.FALSE.equals(app.getEnabled())) {
            throw new AiBusinessException(AiErrorCode.APP_DISABLED, appId);
        }
        Model model = aiModelResolver.apply(app.getModelId());
        Toolkit toolkit = toolkitAssembler.build(app);
        int maxIters = Objects.requireNonNullElse(
                app.getMaxIters(), aiProperties.getRuntime().getDefaultMaxIters());
        AppType appType = AppType.of(Objects.toString(app.getAppType(), AppType.CHAT.getCode()));
        if (appType == AppType.WORKSPACE) {
            return buildWorkspace(app, tenantId, model, toolkit, maxIters);
        }
        return buildChat(app, tenantId, model, toolkit, maxIters);
    }

    /** CHAT 型：纯 DB 配置，关闭所有 workspace 能力。 */
    private HarnessAgent buildChat(AppEntity app, String tenantId, Model model, Toolkit toolkit, int maxIters) {
        log.info("构建 CHAT Agent: app={}, tenant={}", app.getId(), tenantId);
        return HarnessAgent.builder()
                .agentId("app:" + app.getId() + ":t:" + tenantId)
                .name(app.getName())
                .sysPrompt(StringUtils.defaultString(app.getSystemPrompt()))
                .model(model)
                .maxIters(maxIters)
                .toolkit(toolkit)
                .stateStore(new InMemoryAgentStateStore())
                .disableFilesystemTools()
                .disableWorkspaceContext()
                .disableAtPathExpansion()
                .disableCompaction()
                .disableToolResultEviction()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableSubagents()
                .disableToolsConfig()
                .disableSessionPersistence()
                .disableMemoryTools()
                .disableMemoryHooks()
                .build();
    }

    /** WORKSPACE 型：per-app workspace + harness 完整能力 + 可选沙箱。 */
    private HarnessAgent buildWorkspace(AppEntity app, String tenantId, Model model, Toolkit toolkit, int maxIters) {
        Path hostWorkspace = workspacePaths.resolveAppWorkspace(tenantId, app.getId());
        try {
            Files.createDirectories(hostWorkspace);
            workspaceScaffolder.scaffold(hostWorkspace, app);
        } catch (IOException e) {
            throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, e);
        }
        boolean selfEvolve = Boolean.TRUE.equals(app.getSelfEvolve());
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .agentId("app:" + app.getId() + ":t:" + tenantId)
                .name(app.getName())
                .sysPrompt(StringUtils.defaultString(app.getSystemPrompt()))
                .model(model)
                .maxIters(maxIters)
                .toolkit(toolkit)
                .stateStore(new InMemoryAgentStateStore())
                .workspace(hostWorkspace);
        // 沙箱后端优先；HOST 或后端扩展未安装时回退 LocalFilesystemSpec
        Optional<SandboxFilesystemSpec> sandboxSpec = sandboxSpecResolver.resolve(app, hostWorkspace);
        if (sandboxSpec.isPresent()) {
            builder.filesystem(sandboxSpec.get());
        } else {
            builder.filesystem(
                    new LocalFilesystemSpec().isolationScope(SandboxSpecResolver.parseIsolationScope(aiProperties)));
        }
        if (!selfEvolve) {
            // ASSISTANT：只读、无自记忆（workspace 由管理员维护）
            builder.disableFilesystemTools().disableMemoryTools().disableMemoryHooks();
        }
        // selfEvolve=true（AUTONOMOUS）：文件工具 + 记忆默认开启，agent 可写 workspace 自演化（审计由 WorkspaceAuditRecorder 记录）
        log.info(
                "构建 WORKSPACE Agent: app={}, tenant={}, selfEvolve={}, sandbox={}",
                app.getId(),
                tenantId,
                selfEvolve,
                sandboxSpec.isPresent() ? "yes" : "HOST");
        return builder.build();
    }

    private String cacheKey(String appId, String tenantId) {
        return appId + "|" + StringUtils.defaultString(tenantId);
    }
}
