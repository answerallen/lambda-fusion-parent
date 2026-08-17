package com.lambda.fusion.ai.runtime;

import com.lambda.fusion.ai.AiConstants.AppType;
import com.lambda.fusion.ai.AiConstants.RagMode;
import com.lambda.fusion.ai.AiConstants.StateStoreType;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.rag.runtime.KnowledgeRetrievalTool;
import com.lambda.fusion.ai.rag.runtime.KnowledgeRetriever;
import com.lambda.fusion.ai.rag.runtime.RagMiddleware;
import com.lambda.fusion.ai.runtime.event.ConfigChangedEvent;
import com.lambda.fusion.ai.runtime.sandbox.SandboxSpecResolver;
import com.lambda.fusion.ai.runtime.state.StateStoreProvider;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceStorage;
import com.lambda.fusion.ai.skill.runtime.SkillRepositoryResolver;
import com.lambda.fusion.ai.subagent.service.SubAgentService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.skill.SkillFilter;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.spec.SandboxFilesystemSpec;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final WorkspaceStorage workspaceStorage;
    private final SandboxSpecResolver sandboxSpecResolver;
    private final List<StateStoreProvider> stateStoreProviders;
    private final SkillRepositoryResolver skillRepositoryResolver;
    private final ObjectProvider<HarnessGateway> gatewayProvider;
    private final ObjectProvider<KnowledgeRetriever> retrieverProvider;
    private final SubAgentService subAgentService;

    private final Map<String, HarnessAgent> cache = new ConcurrentHashMap<>();

    /**
     * 状态存储缓存：同 {@code (appId, tenantId)} 共享同一实例。Agent 因配置变更重建后仍复用原存储，
     * 进程内（MEMORY）会话状态不随重建丢失；存储类型变更需重启生效。缓存键与 {@link #cache} 一致，
     * Agent 缓存失效时刻意不清空本缓存（保留会话记忆）。
     */
    private final Map<String, AgentStateStore> stateStores = new ConcurrentHashMap<>();

    /**
     * 获取或构建 Agent。缓存键 {@code appId|tenantId}，{@link ConcurrentHashMap#computeIfAbsent} 保证首次构建线程安全。
     */
    public HarnessAgent getOrBuild(String appId, String tenantId) {
        String cacheKey = cacheKey(appId, tenantId);
        return cache.computeIfAbsent(cacheKey, k -> build(appId, tenantId));
    }

    /** 获取或创建同 {@code (appId, tenantId)} 共享的状态存储。 */
    private AgentStateStore sharedStateStore(String appId, String tenantId) {
        return stateStores.computeIfAbsent(
                cacheKey(appId, tenantId), key -> resolveStateStore(aiProperties, stateStoreProviders));
    }

    public String buildStableAgentId(String appId, String tenantId) {
        return workspaceStorage.stableAgentId(appId, tenantId);
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
        String source = event.remote() ? "远端" : "本地";
        if (event.appId() == null) {
            log.info("AI 配置变更({})，失效全部 Agent 缓存", source);
            invalidateAll();
        } else {
            log.info("应用 {} 配置变更({})，失效对应 Agent 缓存", event.appId(), source);
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
        registerKnowledgeTool(toolkit, app);
        int maxIters = Objects.requireNonNullElse(
                app.getMaxIters(), aiProperties.getRuntime().getDefaultMaxIters());
        AppType appType = AppType.of(Objects.toString(app.getAppType(), AppType.CHAT.getCode()));
        List<MiddlewareBase> middlewares = resolveMiddlewares(app);
        HarnessAgent agent;
        String stableAgentId = buildStableAgentId(app.getId(), tenantId);
        if (appType == AppType.WORKSPACE) {
            agent = buildWorkspace(
                    stableAgentId, app, tenantId, model, toolkit, maxIters, middlewares, resolveSubAgents(app));
        } else {
            agent = buildChat(stableAgentId, app, tenantId, model, toolkit, maxIters, middlewares);
        }
        registerWithGateway(stableAgentId, agent);
        return agent;
    }

    // 解析绑定的启用中子代理为 harness 声明（声明构建期固化；子代理变更由 ConfigChangedEvent 全量失效重建）
    private List<SubagentDeclaration> resolveSubAgents(AppEntity app) {
        if (app.getSubAgentIds() == null || app.getSubAgentIds().isEmpty()) {
            return List.of();
        }
        return subAgentService.listEnabledByIds(app.getSubAgentIds()).stream()
                .map(SubAgentDeclarationMapper::toDeclaration)
                .toList();
    }

    /**
     * 按 app 的 {@code knowledgeBaseIds} 绑定解析运行时中间件：检索功能启用、绑定非空且
     * 模式为 GENERIC/BOTH 时挂载 {@link RagMiddleware}（对话时实时检索注入）；绑定变更复用
     * {@code ConfigChangedEvent} 失效机制重建 agent。
     */
    private List<MiddlewareBase> resolveMiddlewares(AppEntity app) {
        RagMode ragMode = resolveRagMode(app);
        if (ragMode != RagMode.GENERIC && ragMode != RagMode.BOTH) {
            return List.of();
        }
        KnowledgeRetriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            return List.of();
        }
        return List.of(new RagMiddleware(
                retriever, app.getKnowledgeBaseIds(), aiProperties.getRag().getMaxInjectChars()));
    }

    // AGENTIC/BOTH 模式：注册 retrieve_knowledge 工具由模型自主检索（构建期绑定 kbIds，
    // 不走 ToolkitAssembler 全局扫描——检索范围必须按 app 隔离）
    private void registerKnowledgeTool(Toolkit toolkit, AppEntity app) {
        RagMode ragMode = resolveRagMode(app);
        if (ragMode != RagMode.AGENTIC && ragMode != RagMode.BOTH) {
            return;
        }
        KnowledgeRetriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            return;
        }
        toolkit.registerTool(new KnowledgeRetrievalTool(retriever, app.getKnowledgeBaseIds()));
        log.info("应用 {} 启用 Agentic 知识检索工具(kbs={})", app.getId(), app.getKnowledgeBaseIds());
    }

    // 知识库检索模式解析：kbIds 为空不启用任何模式；ragMode 空/未知按 GENERIC（向后兼容，
    // 非法值在应用保存时已校验拦截）。包级可见便于单测
    static RagMode resolveRagMode(AppEntity app) {
        if (app.getKnowledgeBaseIds() == null || app.getKnowledgeBaseIds().isEmpty()) {
            return null;
        }
        RagMode ragMode = RagMode.of(app.getRagMode());
        return ragMode != null ? ragMode : RagMode.GENERIC;
    }

    private void registerWithGateway(String stableAgentId, HarnessAgent agent) {
        HarnessGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            return;
        }
        gateway.registerAgent(stableAgentId, agent);
    }

    /**
     * 构建 HITL 权限上下文：BYPASS 模式 + {@code @RequireConfirm} 工具的 ASK 规则。ask 优先于
     * BYPASS，故仅名单内工具触发确认，其余放行（DEFAULT 模式会全拦）。无名单返回 null（向后兼容）。
     */
    private PermissionContextState buildPermissionContext() {
        Set<String> askToolNames = toolkitAssembler.getAskToolNames();
        if (askToolNames.isEmpty()) {
            return null;
        }
        PermissionContextState.Builder builder =
                PermissionContextState.builder().mode(PermissionMode.BYPASS);
        for (String toolName : askToolNames) {
            builder.addAskRule(toolName, new PermissionRule(toolName, null, PermissionBehavior.ASK, "userSettings"));
        }
        return builder.build();
    }

    /**
     * 按配置解析 Agent 状态存储：MEMORY/FILE 内置；MYSQL/POSTGRES/REDIS/OSS/COS 分发到匹配的
     * {@link StateStoreProvider}。显式配置非 MEMORY/FILE 类型时，扩展缺失或创建失败必须抛出
     * {@link AiErrorCode#CONFIGURATION_ERROR}，禁止静默回退 MEMORY。
     */
    static AgentStateStore resolveStateStore(AiProperties props) {
        return resolveStateStore(props, List.of());
    }

    static AgentStateStore resolveStateStore(AiProperties props, List<StateStoreProvider> providers) {
        AiProperties.StateStore cfg = props.getStateStore();
        String configuredType = cfg.getType();
        StateStoreType type = StateStoreType.of(configuredType);
        boolean explicit = StringUtils.isNotBlank(configuredType);

        if (type == StateStoreType.FILE) {
            return new JsonFileAgentStateStore(resolveStateRoot(props));
        }
        if (type == null || type == StateStoreType.MEMORY) {
            if (explicit && type == null) {
                throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, "未知的 Agent 状态存储类型: " + configuredType);
            }
            return new InMemoryAgentStateStore();
        }

        try {
            return providers.stream()
                    .filter(p -> p.type() == type)
                    .findFirst()
                    .map(StateStoreProvider::create)
                    .orElseThrow(() -> new AiBusinessException(
                            AiErrorCode.CONFIGURATION_ERROR, "Agent 状态存储类型 " + type + " 的扩展未安装"));
        } catch (AiBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agent 状态存储 {} 创建失败", type, e);
            throw new AiBusinessException(
                    AiErrorCode.CONFIGURATION_ERROR, "Agent 状态存储 " + type + " 创建失败: " + safeMessage(e));
        }
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    /**
     * FILE 状态目录优先使用显式配置，其次 workspace.root/state，最后 ~/.agentscope/fusion/state。
     */
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

    /**
     * 按 app 的 {@code skillsAllow}/{@code skillsDeny} 解析 harness {@link SkillFilter}。
     *
     * <p>{@code skillsAllow} 非空 -> 仅这些技能；否则 {@code skillsDeny} 非空 -> 除这些外；均空 -> 全放行。
     * allow 优先于 deny。作用于所有技能源（市场 DB repo + workspace 文件系统 repo）。
     */
    static SkillFilter resolveSkillFilter(AppEntity app) {
        List<String> allow = app.getSkillsAllow();
        if (allow != null && !allow.isEmpty()) {
            return SkillFilter.only(allow.toArray(new String[0]));
        }
        List<String> deny = app.getSkillsDeny();
        if (deny != null && !deny.isEmpty()) {
            return SkillFilter.except(deny.toArray(new String[0]));
        }
        return SkillFilter.all();
    }

    private HarnessAgent buildChat(
            String stableAgentId,
            AppEntity app,
            String tenantId,
            Model model,
            Toolkit toolkit,
            int maxIters,
            List<MiddlewareBase> middlewares) {
        log.info("构建 CHAT Agent: app={}, tenant={}", app.getId(), tenantId);
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .agentId(stableAgentId)
                .name(app.getName())
                .sysPrompt(StringUtils.defaultString(app.getSystemPrompt()))
                .model(model)
                .maxIters(maxIters)
                .toolkit(toolkit)
                .permissionContext(buildPermissionContext())
                .stateStore(sharedStateStore(app.getId(), tenantId))
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
                .disableMemoryHooks();
        if (!middlewares.isEmpty()) {
            builder.middlewares(middlewares);
        }
        return builder.build();
    }

    private HarnessAgent buildWorkspace(
            String stableAgentId,
            AppEntity app,
            String tenantId,
            Model model,
            Toolkit toolkit,
            int maxIters,
            List<MiddlewareBase> middlewares,
            List<SubagentDeclaration> subAgents) {
        Path hostWorkspace = workspaceStorage.initializeWorkspace(tenantId, app);
        boolean selfEvolve = Boolean.TRUE.equals(app.getSelfEvolve());
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .agentId(stableAgentId)
                .name(app.getName())
                .sysPrompt(StringUtils.defaultString(app.getSystemPrompt()))
                .model(model)
                .maxIters(maxIters)
                .toolkit(toolkit)
                .permissionContext(buildPermissionContext())
                .stateStore(sharedStateStore(app.getId(), tenantId))
                .workspace(hostWorkspace)
                .memory(resolveMemoryConfig(aiProperties))
                .skillFilter(resolveSkillFilter(app));
        AgentSkillRepository skillRepo = skillRepositoryResolver.resolve();
        if (skillRepo != null) {
            builder.skillRepository(skillRepo);
        }
        Optional<SandboxFilesystemSpec> sandboxSpec = sandboxSpecResolver.resolve(app, hostWorkspace);
        workspaceStorage.configureFilesystem(builder, sandboxSpec, hostWorkspace, stableAgentId);
        if (!selfEvolve) {
            builder.disableFilesystemTools().disableMemoryTools().disableMemoryHooks();
        }
        if (!middlewares.isEmpty()) {
            builder.middlewares(middlewares);
        }
        if (!subAgents.isEmpty()) {
            // 编程式注册 DB 子代理声明；与 workspace/subagents/*.md 文件扫描合并（同名文件覆盖 DB）
            builder.subagents(subAgents);
            // declaration.model 存 fusion 模型ID，桥接 ModelResolver 按 ai_llm_model 解析；
            // 不影响主模型（已由 .model(model) 显式指定）
            builder.modelResolver(modelResolver);
        }
        // 自演化应用保留文件工具和记忆钩子；本轮文件变更由 WorkspaceAuditRecorder 审计。
        log.info(
                "构建 WORKSPACE Agent: app={}, tenant={}, selfEvolve={}, storage={}, sandbox={}",
                app.getId(),
                tenantId,
                selfEvolve,
                workspaceStorage.type(),
                sandboxSpec.isPresent() ? "yes" : "HOST");
        return builder.build();
    }

    /** 将系统级记忆刷新策略映射为 AgentScope 官方配置，其余记忆参数保留框架默认值。 */
    static MemoryConfig resolveMemoryConfig(AiProperties properties) {
        AiProperties.Memory.Flush flush = properties.getMemory().getFlush();
        MemoryConfig.FlushTrigger trigger =
                switch (flush.getMode()) {
                    case ALWAYS -> MemoryConfig.FlushTrigger.always();
                    case THROTTLED -> MemoryConfig.FlushTrigger.throttled(flush.getMinGap());
                    case NEVER -> MemoryConfig.FlushTrigger.never();
                };
        return MemoryConfig.builder().flushTrigger(trigger).build();
    }

    // 格式与 {@link #invalidateApp} 的 key 前缀匹配逻辑耦合
    private String cacheKey(String appId, String tenantId) {
        return appId + "|" + StringUtils.defaultString(tenantId);
    }
}
