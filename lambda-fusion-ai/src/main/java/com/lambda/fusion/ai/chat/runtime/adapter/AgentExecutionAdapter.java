package com.lambda.fusion.ai.chat.runtime.adapter;

import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.gateway.RuntimeProperty;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.tool.ToolResultMessageBuilder;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.SessionIdUtils;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Agent 执行适配器。
 *
 * <p>统一直连 Agent 与 Harness 网关两种调用模式，并封装状态会话标识、状态读取和执行中断。
 *
 * @author Jin
 */
@Slf4j
public final class AgentExecutionAdapter {

    private static final String CHANNEL_ID = "fusion-chat";

    private final HarnessAgent agent;
    private final HarnessGateway gateway;
    private final String runId;
    private final String sessionId;
    private final String userId;
    private final String tenantId;
    private final MsgContext gatewayContext;
    private final OutboundAddress outboundAddress;
    private final String stateSessionId;

    /**
     * 创建 Agent 执行适配器。
     *
     * @param agent Agent 实例
     * @param gateway Harness 网关；直连模式下为 {@code null}
     * @param routingAgentId 网关路由标识，须与 AgentFactory 注册进网关的键一致；
     *        注意不能用 {@link HarnessAgent#getAgentId()}（其为随机实例 UUID，并非注册键）
     * @param run 运行实体
     * @param session 会话实体
     * @param tenantId 已归一化的租户标识
     */
    public AgentExecutionAdapter(
            HarnessAgent agent,
            HarnessGateway gateway,
            String routingAgentId,
            ChatRunEntity run,
            ChatSessionEntity session,
            String tenantId) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.gateway = gateway;
        this.runId = Objects.requireNonNull(run.getId(), "run.id");
        this.sessionId = Objects.requireNonNull(run.getSessionId(), "run.sessionId");
        this.userId = session.getUserId();
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (gateway == null) {
            this.gatewayContext = null;
            this.outboundAddress = null;
            this.stateSessionId = sessionId;
            return;
        }
        this.gatewayContext = new MsgContext(
                CHANNEL_ID,
                tenantId,
                sessionId,
                null,
                null,
                buildExtra(routingAgentId, session.getAppId(), sessionId, tenantId),
                userId);
        this.outboundAddress = OutboundAddress.direct(CHANNEL_ID, CHANNEL_ID + ":DIRECT:" + sessionId);
        this.stateSessionId = "gw-" + SessionIdUtils.deterministicHash(gatewayContext.canonicalKey());
    }

    /**
     * 启动 Agent 事件流。
     *
     * @param message 输入消息
     * @return Agent 事件流
     */
    public Flux<AgentEvent> stream(Msg message) {
        if (gateway != null) {
            return gateway.runStream(gatewayContext, List.of(message), outboundAddress);
        }
        RuntimeContext context = RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .put(RuntimeProperty.KEY_TENANT_ID, tenantId)
                .build();
        return agent.streamEvents(message, context);
    }

    /**
     * 读取 Agent 状态中当前待确认的工具调用。
     *
     * <p>口径与 AgentScope {@code getPendingToolUseIds} 一致：只取最后一条助手消息中的 ASKING 块。
     * 该口径是 Agent 实际阻塞/待确认的权威界定，也是确认三方校验（快照=决策=Agent）的共同基准；
     * 不得扫描全上下文——旧消息残留的未收尾 ASKING 块不属于当前确认批次，计入会使三方严格相等误判失败。
     *
     * @return 当前待确认工具调用
     * @throws AiBusinessException Agent 状态不可用或不存在待确认工具调用
     */
    public List<ToolUseBlock> readAskingToolBlocks() {
        try {
            var state = agent.getDelegate().getAgentState(userId, stateSessionId);
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
                // 最后一条助手消息无 ASKING：当前无待确认批次（不回退到更早消息）。
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
        agent.getDelegate().interrupt(userId, stateSessionId);
    }

    /**
     * 将状态会话中遗留的未决工具调用补写为用户拒绝结果并落盘。
     *
     * <p>AgentScope 约定：ASKING 或尚未落结果的工具调用会阻塞同一状态会话的后续调用。Run 停止、
     * 确认超时或异常终结时调用本方法，等价于确认恢复的拒绝分支，使会话可继续对话。幂等：无未决调用时为空操作。
     */
    public void denyPendingToolCalls() {
        ReActAgent delegate = agent.getDelegate();
        AgentState state = delegate.getAgentState(userId, stateSessionId);
        if (state == null || state.getContext() == null) {
            return;
        }
        // 与 AgentScope getPendingToolUseIds 口径一致：最后一条助手消息的工具调用中，尚无对应结果的视为未决。
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
        delegate.saveAgentState(userId, stateSessionId);
    }

    private AiBusinessException confirmationContextUnavailable() {
        return new AiBusinessException(AiErrorCode.CHAT_RUN_CONFIRM_CONTEXT_UNAVAILABLE, runId);
    }

    private static Map<String, String> buildExtra(String agentId, String appId, String sessionId, String tenantId) {
        return Map.of(
                RuntimeProperty.KEY_AGENT_ID,
                agentId,
                RuntimeProperty.KEY_APP_ID,
                appId,
                RuntimeProperty.KEY_LF_SESSION_ID,
                sessionId,
                RuntimeProperty.KEY_TENANT_ID,
                tenantId);
    }
}
