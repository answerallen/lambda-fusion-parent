package com.lambda.fusion.ai.runtime;

import com.lambda.fusion.ai.AiConstants.AppType;
import com.lambda.fusion.ai.AiConstants.StateStoreType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.event.ConfigChangedEvent;
import com.lambda.fusion.ai.runtime.sandbox.SandboxSpecResolver;
import com.lambda.fusion.ai.runtime.state.StateStoreProvider;
import com.lambda.fusion.ai.runtime.workspace.WorkspacePaths;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceScaffolder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
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
public class AgentFactory {

    private final AppService appService;
    private final ModelResolver modelResolver;
    private final ToolkitAssembler toolkitAssembler;
    private final AiProperties aiProperties;
    private final WorkspacePaths workspacePaths;
    private final WorkspaceScaffolder workspaceScaffolder;
    private final SandboxSpecResolver sandboxSpecResolver;
    private final List<StateStoreProvider> stateStoreProviders;
    private final ObjectProvider<HarnessGateway> gatewayProvider;

    private final Map<String, HarnessAgent> cache = new ConcurrentHashMap<>();

    /**
     * 获取或构建 Agent。缓存键 {@code appId|tenantId}，{@link ConcurrentHashMap#computeIfAbsent} 保证首次构建线程安全。
     */
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
    public void onConfigChanged(ConfigChangedEvent event) {
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
        Model model = modelResolver.apply(app.getModelId());
        Toolkit toolkit = toolkitAssembler.build(app);
        int maxIters = Objects.requireNonNullElse(
                app.getMaxIters(), aiProperties.getRuntime().getDefaultMaxIters());
        AppType appType = AppType.of(Objects.toString(app.getAppType(), AppType.CHAT.getCode()));
        HarnessAgent agent;
        if (appType == AppType.WORKSPACE) {
            agent = buildWorkspace(app, tenantId, model, toolkit, maxIters);
        } else {
            agent = buildChat(app, tenantId, model, toolkit, maxIters);
        }
        registerWithGateway(agent);
        return agent;
    }

    private void registerWithGateway(HarnessAgent agent) {
        HarnessGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            return;
        }
        gateway.registerAgent(agent.getAgentId(), agent);
    }

    /**
     * 按配置解析 Agent 状态存储：MEMORY/FILE 内置；MYSQL/POSTGRES/REDIS/OSS/COS 分发到匹配的
     * {@link StateStoreProvider}（扩展未安装或创建失败时回退 MEMORY，保证启动不阻塞）。
     */
    static AgentStateStore resolveStateStore(AiProperties props) {
        return resolveStateStore(props, List.of());
    }

    static AgentStateStore resolveStateStore(AiProperties props, List<StateStoreProvider> providers) {
        AiProperties.StateStore cfg = props.getStateStore();
        StateStoreType type = StateStoreType.of(Objects.toString(cfg.getType(), StateStoreType.MEMORY.getCode()));
        if (type == StateStoreType.FILE) {
            return new JsonFileAgentStateStore(resolveStateRoot(props));
        }
        if (type == StateStoreType.MEMORY) {
            return new InMemoryAgentStateStore();
        }
        // 分布式后端：按 type 找匹配 provider
        try {
            AgentStateStore store = providers.stream()
                    .filter(p -> p.type() == type)
                    .findFirst()
                    .map(StateStoreProvider::create)
                    .orElse(null);
            if (store != null) {
                return store;
            }
            log.warn("状态存储 {} 的扩展未安装或客户端缺失，回退 MEMORY", type);
        } catch (Exception e) {
            log.warn("状态存储 {} 创建失败，回退 MEMORY: {}", type, e.getMessage());
        }
        return new InMemoryAgentStateStore();
    }

    // FILE 模式根目录：优先配置值，其次 {@code workspace.root/state}，最后 {@code ~/.agentscope/fusion/state}
    private static Path resolveStateRoot(AiProperties props) {
        String configured = props.getStateStore().getRoot();
        if (StringUtils.isNotBlank(configured)) {
            return Path.of(configured);
        }
        String wsRoot = props.getWorkspace().getRoot();
        if (StringUtils.isNotBlank(wsRoot)) {
            return Path.of(wsRoot, "state");
        }
        return Paths.get(System.getProperty("user.home"), ".agentscope", "fusion", "state");
    }

    // CHAT 型：纯 DB 配置，关闭所有 workspace 能力
    private HarnessAgent buildChat(AppEntity app, String tenantId, Model model, Toolkit toolkit, int maxIters) {
        log.info("构建 CHAT Agent: app={}, tenant={}", app.getId(), tenantId);
        return HarnessAgent.builder()
                .agentId("app:" + app.getId() + ":t:" + tenantId)
                .name(app.getName())
                .sysPrompt(StringUtils.defaultString(app.getSystemPrompt()))
                .model(model)
                .maxIters(maxIters)
                .toolkit(toolkit)
                .stateStore(resolveStateStore(aiProperties, stateStoreProviders))
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

    // WORKSPACE 型：per-app workspace + harness 完整能力 + 可选沙箱
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
                .stateStore(resolveStateStore(aiProperties, stateStoreProviders))
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

    // 格式与 {@link #invalidateApp} 的 key 前缀匹配逻辑耦合
    private String cacheKey(String appId, String tenantId) {
        return appId + "|" + StringUtils.defaultString(tenantId);
    }
}
