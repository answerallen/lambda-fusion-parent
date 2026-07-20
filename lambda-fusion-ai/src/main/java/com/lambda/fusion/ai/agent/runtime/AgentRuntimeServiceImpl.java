package com.lambda.fusion.ai.agent.runtime;

import com.lambda.fusion.ai.apps.mapper.AppsMapper;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.exception.AiBusinessException;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentProvider;
import io.agentscope.core.tool.subagent.SubAgentTool;
import io.agentscope.harness.agent.HarnessAgent;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link AgentRuntimeService} 实现：按 session（appId -> AppEntity 模板 + 执行参数快照）+ 请求 override
 * 构造 {@link HarnessAgent}，经 {@code streamEvents}/{@code call} 执行。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeServiceImpl implements AgentRuntimeService {

    private final ModelClientFactory modelClientFactory;
    private final AgentScopeRuntimeProperties properties;
    private final AppsMapper appsMapper;
    private final ObjectProvider<AgentStateStore> agentStateStoreProvider;
    private final ToolToolkitAdapter toolToolkitAdapter;
    private final KnowledgeFactory knowledgeFactory;
    private final McpClientAdapter mcpClientAdapter;
    private final SubagentSpecParser subagentSpecParser;
    private final ToolGroupSpecParser toolGroupSpecParser;
    private final MiddlewareFactory middlewareFactory;

    @Override
    public Flux<AgentEvent> run(ChatSessionEntity session, SendMessage input) {
        return Flux.defer(() -> {
                    HarnessAgent agent = buildAgent(mergeOverride(templateFromSession(session), input), true);
                    RuntimeContext ctx = buildContext(session);
                    Msg userMsg = Msg.builder()
                            .role(MsgRole.USER)
                            .textContent(input.getContent())
                            .build();
                    return agent.streamEvents(userMsg, ctx);
                })
                .doOnError(e -> log.error("AgentScope 流式执行失败, sessionId={}", session.getId(), e));
    }

    @Override
    public Mono<Msg> call(String appId, String input) {
        return Mono.defer(() -> {
                    AppEntity app = appsMapper.selectById(appId);
                    if (app == null) {
                        throw AiBusinessException.appNotFound(appId);
                    }
                    // one-shot 无 session：stateless（不挂 stateStore），sessionId 留空由 agent 默认
                    HarnessAgent agent = buildAgent(templateFromApp(app), false);
                    RuntimeContext.Builder ctxBuilder = RuntimeContext.builder();
                    if (app.getTenantId() != null) {
                        ctxBuilder.put("tenantId", app.getTenantId());
                    }
                    RuntimeContext ctx = ctxBuilder.build();
                    Msg userMsg =
                            Msg.builder().role(MsgRole.USER).textContent(input).build();
                    return agent.call(userMsg, ctx);
                })
                .doOnError(e -> log.error("AgentScope one-shot 调用失败, appId={}", appId, e));
    }

    @Override
    public Flux<AgentEvent> resume(ChatSessionEntity session, SendMessage input) {
        // HITL 恢复：同一 sessionId 经 AgentStateStore 恢复状态，streamEvents 接续 HintBlockEvent 中断点
        return run(session, input);
    }

    // ==================== agent 构造 ====================

    /** 已解析的 agent 构造参数（优先级：请求 override > session 快照 > app 模板）。 */
    private record AgentTemplate(
            String name,
            String description,
            String sysPrompt,
            String modelId,
            BigDecimal temperature,
            Integer maxTokens,
            String tenantId,
            List<String> kbIds,
            Integer retrievalTopK,
            String ragMode,
            List<String> toolIds,
            List<String> mcpServerIds,
            String subagentSpec,
            String toolGroups,
            String middlewareConfig) {}

    private HarnessAgent buildAgent(AgentTemplate t, boolean stateful) {
        Model model = modelClientFactory.get(t.modelId());
        List<SimpleKnowledge> kbs = resolveKnowledgeBases(t.kbIds());
        boolean agentic = isAgenticRag(t.ragMode());
        boolean staticRag = isStaticRag(t.ragMode());
        AgentStateStore stateStore = stateful ? agentStateStoreProvider.getIfAvailable() : null;
        Toolkit toolkit =
                buildToolkit(t.kbIds(), t.retrievalTopK(), kbs, agentic, t.toolIds(), t.mcpServerIds(), t.toolGroups());
        registerSubagents(toolkit, t, stateStore);
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(StringUtils.hasText(t.name()) ? t.name() : "agent")
                .description(t.description() != null ? t.description() : "")
                .model(model)
                .maxIters(properties.getDefaultMaxIters())
                .modelExecutionConfig(buildModelExecutionConfig())
                .toolkit(toolkit);
        if (StringUtils.hasText(t.sysPrompt())) {
            builder.sysPrompt(t.sysPrompt());
        }
        GenerateOptions options = buildGenerateOptions(t.temperature(), t.maxTokens());
        if (options != null) {
            builder.generateOptions(options);
        }
        if (staticRag && !kbs.isEmpty()) {
            builder.middleware(new RagMiddleware(kbs, t.retrievalTopK()));
        }
        List<MiddlewareBase> middlewares = middlewareFactory.build(t.middlewareConfig());
        if (!middlewares.isEmpty()) {
            builder.middlewares(middlewares);
        }
        if (stateful && stateStore != null) {
            builder.stateStore(stateStore);
        }
        return builder.build();
    }

    /**
     * 合并请求 override 到模板（优先级：请求参数 > session 快照 > app 模板）。仅执行参数
     * （modelId/temperature/maxTokens/kbIds）可 override；结构化配置（ragMode 等）从 robot 实时读。
     */
    private AgentTemplate mergeOverride(AgentTemplate t, SendMessage input) {
        return new AgentTemplate(
                t.name(),
                t.description(),
                t.sysPrompt(),
                input.getLlmModelId() != null ? input.getLlmModelId() : t.modelId(),
                input.getTemperature() != null ? input.getTemperature() : t.temperature(),
                input.getMaxTokens() != null ? input.getMaxTokens() : t.maxTokens(),
                t.tenantId(),
                input.getKbIds() != null && !input.getKbIds().isEmpty() ? input.getKbIds() : t.kbIds(),
                t.retrievalTopK(),
                t.ragMode(),
                t.toolIds(),
                t.mcpServerIds(),
                t.subagentSpec(),
                t.toolGroups(),
                t.middlewareConfig());
    }

    /**
     * 构造 Toolkit：工具组（{@code createToolGroup}）+ 本地 @Tool（经 {@link ToolToolkitAdapter#registerTools}
     * 按组绑定）+ 知识库检索工具（agentic 且 kbIds 非空时注册 {@link KnowledgeRetrievalTool}）+ MCP。
     */
    private Toolkit buildToolkit(
            List<String> kbIds,
            Integer retrievalTopK,
            List<SimpleKnowledge> kbs,
            boolean agentic,
            List<String> toolIds,
            List<String> mcpServerIds,
            String toolGroupsSpec) {
        Toolkit toolkit = new Toolkit();
        Map<String, String> toolGroupBindings = registerToolGroups(toolkit, toolGroupsSpec);
        toolToolkitAdapter.registerTools(toolkit, toolIds, toolGroupBindings);
        if (agentic && kbs != null && !kbs.isEmpty()) {
            toolkit.registerTool(new KnowledgeRetrievalTool(kbs, retrievalTopK));
            log.debug("AgentRuntimeServiceImpl: 已注册知识库检索工具，kbIds: {}", kbIds);
        }
        if (mcpServerIds != null) {
            for (String serverId : mcpServerIds) {
                try {
                    toolkit.registerMcpClient(mcpClientAdapter.get(serverId)).block();
                    log.debug("AgentRuntimeServiceImpl: 已注册 MCP 工具，serverId: {}", serverId);
                } catch (Exception e) {
                    log.warn("AgentRuntimeServiceImpl: MCP 注册失败，跳过 serverId={}", serverId, e);
                }
            }
        }
        return toolkit;
    }

    /**
     * 解析 {@code subagentSpec} -> {@link SubagentSpecDto} 列表，每个 spec 构造子 {@link HarnessAgent}
     * （经 {@link SubAgentProvider} 每次调用产出新实例，复用 {@code buildAgent}）并注册为
     * {@link SubAgentTool}。子 agent 复用主 agent 的 {@link AgentStateStore}（会话隔离），各自
     * model/toolkit 按 spec 装配；一层不嵌套（子 template 的 subagentSpec=null，避免递归循环）。
     */
    private void registerSubagents(Toolkit toolkit, AgentTemplate parent, AgentStateStore stateStore) {
        List<SubagentSpecDto> specs = subagentSpecParser.parse(parent.subagentSpec());
        for (SubagentSpecDto spec : specs) {
            AgentTemplate child = new AgentTemplate(
                    spec.name(),
                    spec.description(),
                    spec.sysPrompt(),
                    spec.modelId(),
                    spec.temperature(),
                    spec.maxTokens(),
                    parent.tenantId(),
                    spec.kbIds() != null ? spec.kbIds() : List.of(),
                    parent.retrievalTopK(),
                    spec.ragMode(),
                    spec.toolIds(),
                    spec.mcpServerIds(),
                    null,
                    null,
                    null);
            SubAgentProvider<HarnessAgent> provider = () -> buildAgent(child, false);
            SubAgentConfig.Builder configBuilder = SubAgentConfig.builder()
                    .description(StringUtils.hasText(spec.description()) ? spec.description() : "");
            if (StringUtils.hasText(spec.toolName())) {
                configBuilder.toolName(spec.toolName());
            }
            if (stateStore != null) {
                configBuilder.stateStore(stateStore);
            }
            toolkit.registerAgentTool(new SubAgentTool(provider, configBuilder.build()));
            log.debug("AgentRuntimeServiceImpl: 已注册子 agent 工具: {}", spec.name());
        }
    }

    /**
     * 解析 {@code toolGroups} -> {@link ToolGroupSpecDto} 列表，每个 spec {@code createToolGroup} 创建组
     * （按 {@code active} 设初始激活态），并产出 toolName -> 组名 的绑定 Map 供
     * {@link ToolToolkitAdapter#registerTools} 把工具归组。组创建失败跳过不阻断。
     */
    private Map<String, String> registerToolGroups(Toolkit toolkit, String toolGroupsSpec) {
        List<ToolGroupSpecDto> specs = toolGroupSpecParser.parse(toolGroupsSpec);
        Map<String, String> toolNameToGroup = new HashMap<>();
        for (ToolGroupSpecDto spec : specs) {
            if (!StringUtils.hasText(spec.name())) {
                continue;
            }
            boolean active = spec.active() == null || spec.active();
            try {
                toolkit.createToolGroup(spec.name(), spec.description() != null ? spec.description() : "", active);
                if (spec.toolNames() != null) {
                    for (String toolName : spec.toolNames()) {
                        toolNameToGroup.put(toolName, spec.name());
                    }
                }
                log.debug("AgentRuntimeServiceImpl: 已创建工具组: {} active={}", spec.name(), active);
            } catch (Exception e) {
                log.warn("AgentRuntimeServiceImpl: 工具组创建失败: {}", spec.name(), e);
            }
        }
        return toolNameToGroup;
    }

    /** RAG static 模式开关：null/HYBRID/STATIC -> true（null 默认 HYBRID）。 */
    private static boolean isStaticRag(String ragMode) {
        return ragMode == null || "HYBRID".equalsIgnoreCase(ragMode) || "STATIC".equalsIgnoreCase(ragMode);
    }

    /** RAG agentic 模式开关：null/HYBRID/AGENTIC -> true（null 默认 HYBRID）。 */
    private static boolean isAgenticRag(String ragMode) {
        return ragMode == null || "HYBRID".equalsIgnoreCase(ragMode) || "AGENTIC".equalsIgnoreCase(ragMode);
    }

    /**
     * 解析 session/app kbIds -> {@link SimpleKnowledge} 列表（含单 KB 装配失败跳过）。供 agentic
     * {@link KnowledgeRetrievalTool} 与 static {@link RagMiddleware} 共享，避免重复装配。全部失败返回
     * 空列表（agent 无 RAG 能力，对话不阻断）。
     */
    private List<SimpleKnowledge> resolveKnowledgeBases(List<String> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        try {
            return knowledgeFactory.get(kbIds);
        } catch (Exception e) {
            log.warn("AgentRuntimeServiceImpl: 知识库装配失败，kbIds={}，agent 将无 RAG 能力", kbIds, e);
            return List.of();
        }
    }

    /**
     * 模型调用容错配置（AgentScope 原生 ExecutionConfig）：
     * 超时 + 重试次数 + 指数退避（1s 起、2 倍）。retryOn 用 AgentScope 内置可重试异常集。
     */
    private ExecutionConfig buildModelExecutionConfig() {
        return ExecutionConfig.builder()
                .timeout(Duration.ofSeconds(properties.getModelTimeoutSeconds()))
                .maxAttempts(properties.getModelMaxAttempts())
                .initialBackoff(Duration.ofSeconds(1))
                .backoffMultiplier(2.0)
                .build();
    }

    private GenerateOptions buildGenerateOptions(BigDecimal temperature, Integer maxTokens) {
        if (temperature == null && maxTokens == null) {
            return null;
        }
        GenerateOptions.Builder b = GenerateOptions.builder();
        if (temperature != null) {
            b.temperature(temperature.doubleValue());
        }
        if (maxTokens != null) {
            b.maxTokens(maxTokens);
        }
        return b.build();
    }

    private RuntimeContext buildContext(ChatSessionEntity session) {
        RuntimeContext.Builder b =
                RuntimeContext.builder().sessionId(session.getId()).userId(session.getUserId());
        if (session.getTenantId() != null) {
            b.put("tenantId", session.getTenantId());
        }
        return b.build();
    }

    // ==================== 模板解析 ====================

    private AgentTemplate templateFromSession(ChatSessionEntity session) {
        AppEntity app = resolveApp(session.getAppId());
        // 快照优先（快照即稳定性）；session 未钉住的回落到 app 模板
        return new AgentTemplate(
                app != null ? app.getName() : "agent",
                app != null ? app.getDescription() : "",
                firstNonBlank(session.getSystemPrompt(), app != null ? app.getSystemPrompt() : null),
                firstNonBlank(session.getLlmModelId(), app != null ? app.getLlmModelId() : null),
                session.getTemperature() != null
                        ? session.getTemperature()
                        : (app != null ? app.getTemperature() : null),
                session.getMaxTokens() != null ? session.getMaxTokens() : (app != null ? app.getMaxTokens() : null),
                session.getTenantId() != null ? session.getTenantId() : (app != null ? app.getTenantId() : null),
                firstNonEmpty(session.getKbIds(), app != null ? app.getKbIds() : null),
                session.getRetrievalTopK() != null
                        ? session.getRetrievalTopK()
                        : (app != null ? app.getRetrievalTopK() : null),
                app != null ? app.getRagMode() : null,
                app != null ? app.getToolIds() : null,
                app != null ? app.getMcpServerIds() : null,
                app != null ? app.getSubagentSpec() : null,
                app != null ? app.getToolGroups() : null,
                app != null ? app.getMiddlewareConfig() : null);
    }

    private AgentTemplate templateFromApp(AppEntity app) {
        return new AgentTemplate(
                app.getName(),
                app.getDescription(),
                app.getSystemPrompt(),
                app.getLlmModelId(),
                app.getTemperature(),
                app.getMaxTokens(),
                app.getTenantId(),
                app.getKbIds(),
                app.getRetrievalTopK(),
                app.getRagMode(),
                app.getToolIds(),
                app.getMcpServerIds(),
                app.getSubagentSpec(),
                app.getToolGroups(),
                app.getMiddlewareConfig());
    }

    private AppEntity resolveApp(String appId) {
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        return appsMapper.selectById(appId);
    }

    private static List<String> firstNonEmpty(List<String> first, List<String> second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        return List.of();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
