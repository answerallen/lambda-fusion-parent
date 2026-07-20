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
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
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
            String ragMode) {}

    private HarnessAgent buildAgent(AgentTemplate t, boolean stateful) {
        Model model = modelClientFactory.get(t.modelId());
        List<SimpleKnowledge> kbs = resolveKnowledgeBases(t.kbIds());
        boolean agentic = isAgenticRag(t.ragMode());
        boolean staticRag = isStaticRag(t.ragMode());
        Toolkit toolkit = buildToolkit(t.kbIds(), t.retrievalTopK(), kbs, agentic);
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
        if (stateful) {
            AgentStateStore store = agentStateStoreProvider.getIfAvailable();
            if (store != null) {
                builder.stateStore(store);
            }
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
                t.ragMode());
    }

    /**
     * 构造 Toolkit：本地 @Tool（经 {@link ToolToolkitAdapter}）+ 知识库检索工具（agentic 模式且 kbIds
     * 非空时注册 {@link KnowledgeRetrievalTool}，agent 自主决定何时检索）。
     */
    private Toolkit buildToolkit(
            List<String> kbIds, Integer retrievalTopK, List<SimpleKnowledge> kbs, boolean agentic) {
        Toolkit toolkit = toolToolkitAdapter.buildToolkit();
        if (agentic && kbs != null && !kbs.isEmpty()) {
            toolkit.registerTool(new KnowledgeRetrievalTool(kbs, retrievalTopK));
            log.debug("AgentRuntimeServiceImpl: 已注册知识库检索工具，kbIds: {}", kbIds);
        }
        return toolkit;
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
                app != null ? app.getRagMode() : null);
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
                app.getRagMode());
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
