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
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.KnowledgeRetrievalTools;
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
 * {@link AgentRuntimeService} 实现：按 session（{@code robotId} -> AppEntity 模板 + 执行参数快照）+ 请求 override
 * 构造 AgentScope {@link HarnessAgent}，经 {@code streamEvents}/{@code call} 执行。
 *
 * <p>运行时参数覆盖规则（{@code docs/refactor/ai-agentscope-refactor.md} §5.2/§5.3）：
 * {@code 请求参数（SendMessage override，待 DTO 扩展） > session 快照 > app 模板}。
 * 当前 {@link SendMessage} 仅有 {@code content}，override 字段（temperature/maxTokens/kbIds/llmModelId）
 * 为 Phase 1 DTO 任务，落地后在此合并。
 *
 * <p>每条消息构造一个 {@link HarnessAgent}（model 经 {@link ModelClientFactory} 缓存，stateStore 为共享 Bean）；
 * 会话状态经 {@link AgentStateStore} 按 sessionId 恢复/持久化，故 agent 无需跨消息复用。
 * 不在流结束后 {@code close()} agent，避免误关共享 stateStore/model（lifecycle 缓存留 Phase 1 跟进）。
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
                    HarnessAgent agent = buildAgent(templateFromSession(session), true);
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
                        throw AiBusinessException.robotNotFound(appId);
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
            List<String> kbIds) {}

    private HarnessAgent buildAgent(AgentTemplate t, boolean stateful) {
        Model model = modelClientFactory.get(t.modelId());
        Toolkit toolkit = buildToolkit(t.kbIds());
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
        if (stateful) {
            AgentStateStore store = agentStateStoreProvider.getIfAvailable();
            if (store != null) {
                builder.stateStore(store);
            }
        }
        return builder.build();
    }

    /**
     * 构造 Toolkit：本地 @Tool（经 {@link ToolToolkitAdapter}）+ 知识库检索工具（若 kbIds 非空，
     * 注册 {@link KnowledgeRetrievalTools}，agent 自主决定何时检索）。
     */
    private Toolkit buildToolkit(List<String> kbIds) {
        Toolkit toolkit = toolToolkitAdapter.buildToolkit();
        if (kbIds != null && !kbIds.isEmpty()) {
            try {
                Knowledge knowledge = knowledgeFactory.get(kbIds);
                if (knowledge != null) {
                    toolkit.registerTool(new KnowledgeRetrievalTools(knowledge));
                    log.debug("AgentRuntimeServiceImpl: 已注册知识库检索工具，kbIds: {}", kbIds);
                }
            } catch (Exception e) {
                // RAG 装配失败不阻断对话，agent 降级为无检索能力
                log.warn("AgentRuntimeServiceImpl: 知识库工具装配失败，kbIds={}，agent 将无 RAG 能力", kbIds, e);
            }
        }
        return toolkit;
    }

    /**
     * 模型调用容错配置（AgentScope 原生 ExecutionConfig，取代旧 Resilience4j Retry/Timeout）：
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
        AppEntity app = resolveApp(session.getRobotId());
        // 快照优先（快照即稳定性，见 §5.2）；session 未钉住的回落到 app 模板
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
                firstNonEmpty(session.getKbIds(), app != null ? app.getKbIds() : null));
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
                app.getKbIds());
    }

    private AppEntity resolveApp(String robotId) {
        if (!StringUtils.hasText(robotId)) {
            return null;
        }
        return appsMapper.selectById(robotId);
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
