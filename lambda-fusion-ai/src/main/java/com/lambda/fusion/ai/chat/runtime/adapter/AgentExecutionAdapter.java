package com.lambda.fusion.ai.chat.runtime.adapter;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.gateway.FusionSubagentGateway;
import com.lambda.fusion.ai.runtime.gateway.RuntimeProperty;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.SubagentExposedEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.tool.ToolResultMessageBuilder;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 对话运行与 {@link HarnessAgent} 之间的执行适配器。内部执行直接调用已选定的 Agent，不再经过 Harness 网关路由；
 * 状态会话统一使用 {@code (userId, ChatSession.id)} 标识，并在此封装事件流、中断和待确认工具调用处理。
 *
 * @author Jin
 */
@Slf4j
public final class AgentExecutionAdapter {

    private final HarnessAgent agent;
    private final String runId;
    private final String sessionId;
    private final String userId;
    private final String appId;
    private final String tenantId;
    private final FusionSubagentGateway subagentGateway;

    /**
     * 创建 Agent 执行适配器。
     *
     * @param agent Agent 实例
     * @param run 运行实体
     * @param session 会话实体
     * @param tenantId 已归一化的租户标识
     */
    public AgentExecutionAdapter(HarnessAgent agent, ChatRunEntity run, ChatSessionEntity session, String tenantId) {
        this(agent, run, session, tenantId, null);
    }

    public AgentExecutionAdapter(
            HarnessAgent agent,
            ChatRunEntity run,
            ChatSessionEntity session,
            String tenantId,
            FusionSubagentGateway subagentGateway) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.runId = Objects.requireNonNull(run.getId(), "run.id");
        this.sessionId = Objects.requireNonNull(run.getSessionId(), "run.sessionId");
        this.userId = session.getUserId();
        this.appId = Objects.requireNonNull(session.getAppId(), "session.appId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.subagentGateway = subagentGateway;
    }

    /**
     * 启动 Agent 事件流。每次调用都创建独立的 {@link RuntimeContext}，并直接调用
     * {@link HarnessAgent#streamEvents}。
     *
     * @param message 输入消息
     * @return Agent 事件流
     */
    public Flux<AgentEvent> stream(Msg message) {
        RuntimeContext context = RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .put(RuntimeProperty.KEY_TENANT_ID, tenantId)
                .put(RuntimeProperty.KEY_APP_ID, appId)
                .put(RuntimeProperty.KEY_LF_SESSION_ID, sessionId)
                .build();
        Flux<AgentEvent> source = agent.streamEvents(message, context);
        if (subagentGateway != null) {
            source = source.doOnNext(this::recordSubagentExposure);
        }
        return endAtHitlPhaseBoundary(source);
    }

    /**
     * 在权限确认阶段的根 {@code AGENT_END} 处结束当前交互源流。
     *
     * <p>AgentScope 会先持久化 {@code ASKING} 状态，再发送根 {@code AGENT_END}，随后才通过
     * {@code concatWith} 订阅记忆冲刷和整理尾部。此处等待根结束事件后再取消上游，既保留可恢复的确认状态，
     * 又避免记忆模型阻塞 Run 进入 {@code AWAITING_CONFIRM}。普通最终回答和子 Agent 事件不受影响。
     */
    private Flux<AgentEvent> endAtHitlPhaseBoundary(Flux<AgentEvent> source) {
        return Flux.defer(() -> {
            AtomicBoolean awaitingConfirmation = new AtomicBoolean();
            return source.doOnNext(event -> {
                        if (isRootEvent(event, AgentEventType.REQUIRE_USER_CONFIRM)) {
                            awaitingConfirmation.set(true);
                        }
                    })
                    .takeUntil(event -> awaitingConfirmation.get() && isRootEvent(event, AgentEventType.AGENT_END));
        });
    }

    private static boolean isRootEvent(AgentEvent event, AgentEventType type) {
        return event.getType() == type && event.getSource() == null;
    }

    private void recordSubagentExposure(AgentEvent event) {
        if (event instanceof SubagentExposedEvent exposedEvent) {
            subagentGateway.recordExposure(exposedEvent, appId, tenantId, userId, sessionId);
        }
    }

    /**
     * 读取 Agent 状态中当前待确认的工具调用。
     *
     * <p>与 AgentScope 的 {@code getPendingToolUseIds} 保持一致，只检查最后一条助手消息中的 ASKING 块。
     * 该范围是快照、用户决策和 Agent 状态三方校验的共同基准；若扫描更早消息，遗留块可能造成误判。
     *
     * @return 当前待确认工具调用
     * @throws AiBusinessException Agent 状态不可用或不存在待确认工具调用
     */
    public List<ToolUseBlock> readAskingToolBlocks() {
        try {
            var state = agent.getDelegate().getAgentState(userId, sessionId);
            if (state == null || state.getContext() == null) {
                throw confirmationContextUnavailable();
            }
            List<Msg> context = state.getContext();
            for (int i = context.size() - 1; i >= 0; i--) {
                Msg message = context.get(i);
                if (message.getRole() != MsgRole.ASSISTANT) {
                    continue;
                }
                List<ToolUseBlock> asking = message.getContentBlocks(ToolUseBlock.class).stream()
                        .filter(tool -> tool.getState() == ToolCallState.ASKING)
                        .toList();
                if (!asking.isEmpty()) {
                    return asking;
                }
                // 最后一条助手消息没有 ASKING 块，表示当前不存在待确认批次，不再回查历史消息。
                break;
            }
        } catch (AiBusinessException exception) {
            throw exception;
        } catch (RuntimeException error) {
            log.warn("读取HITL Agent状态失败: runId={}", runId, error);
            throw confirmationContextUnavailable();
        }
        throw confirmationContextUnavailable();
    }

    /** 中断当前 Agent 状态会话。 */
    public void interrupt() {
        agent.getDelegate().interrupt(userId, sessionId);
    }

    /**
     * 将状态会话中遗留的工具调用补写为用户拒绝结果并保存。
     *
     * <p>ASKING 或缺少结果的工具调用会阻塞同一状态会话的后续请求。运行停止、确认超时或异常终结时调用本方法，
     * 可闭合未决调用并恢复会话可用性；不存在未决调用时不产生修改。
     */
    public void denyPendingToolCalls() {
        ReActAgent delegate = agent.getDelegate();
        AgentState state = delegate.getAgentState(userId, sessionId);
        if (state == null || state.getContext() == null) {
            return;
        }
        // 与 AgentScope 保持同一判定范围：最后一条助手消息中缺少对应结果的工具调用视为未决。
        Set<String> resultIds = state.getContext().stream()
                .flatMap(message -> message.getContentBlocks(ToolResultBlock.class).stream())
                .map(ToolResultBlock::getId)
                .collect(Collectors.toSet());
        List<ToolUseBlock> pending = new ArrayList<>();
        for (int i = state.getContext().size() - 1; i >= 0; i--) {
            Msg message = state.getContext().get(i);
            if (message.getRole() != MsgRole.ASSISTANT) {
                continue;
            }
            pending = message.getContentBlocks(ToolUseBlock.class).stream()
                    .filter(tool -> !resultIds.contains(tool.getId()))
                    .toList();
            break;
        }
        if (pending.isEmpty()) {
            return;
        }
        for (ToolUseBlock tool : pending) {
            ToolResultBlock denied = ToolResultBlock.text("Permission denied by user")
                    .withIdAndName(tool.getId(), tool.getName())
                    .withState(ToolResultState.DENIED);
            state.contextMutable().add(ToolResultMessageBuilder.buildToolResultMsg(denied, tool, agent.getName()));
        }
        delegate.saveAgentState(userId, sessionId);
    }

    private AiBusinessException confirmationContextUnavailable() {
        return new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, runId);
    }
}
