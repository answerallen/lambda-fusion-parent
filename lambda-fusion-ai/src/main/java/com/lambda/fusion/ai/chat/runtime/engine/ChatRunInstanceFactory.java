package com.lambda.fusion.ai.chat.runtime.engine;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.adapter.AgentExecutionAdapter;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.snapshot.ChatRunSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.gateway.FusionSubagentGateway;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.concurrent.ScheduledExecutorService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 执行实例工厂：统一构造 {@link ChatRunInstance}。带 Agent 的实例只用于新 Run 或持久化 HITL 的显式继续，
 * 无 Agent 的纯落库实例仅用于终结；构造依赖集中在工厂，调度器由协调器按次传入，
 * 活动实例注册表由协调器的注册表组件独占，工厂不持有、查询或修改。
 *
 * @author Jin
 */
@Component
@RequiredArgsConstructor
public class ChatRunInstanceFactory {

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final AgentFactory agentFactory;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final ObjectProvider<FusionSubagentGateway> subagentGatewayProvider;
    private final AiProperties properties;

    /** 为当前节点刚创建的 Run 构造带 Agent 的完整执行实例。 */
    public ChatRunInstance createExecution(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        return createAgentBacked(run, session, scheduler);
    }

    /**
     * 为已持久化在 HITL 边界的 Run 重建本地确认实例。此方法只在用户显式确认或放弃时调用，
     * 不启动旧阶段、不接管正在执行的工具。
     */
    public ChatRunInstance createPausedConfirmation(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        return createAgentBacked(run, session, scheduler);
    }

    private ChatRunInstance createAgentBacked(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        eventStore.registerLocalRun(run.getId());
        return newInstance(run, session, scheduler, createAgentExecution(run, session));
    }

    private AgentExecutionAdapter createAgentExecution(ChatRunEntity run, ChatSessionEntity session) {
        String tenantId = tenantId(session);
        HarnessAgent agent = agentFactory.getOrBuild(session.getAppId(), tenantId);
        FusionSubagentGateway subagentGateway = subagentGatewayProvider.getIfAvailable();
        if (subagentGateway != null) {
            subagentGateway.configureAgent(agent);
        }
        return new AgentExecutionAdapter(agent, run, session, tenantId, subagentGateway);
    }

    /** 构造无 Agent 的纯终结实例（仅用于落终态，不闭合或恢复 Agent 状态）。 */
    public ChatRunInstance createTerminalOnly(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        return newInstance(run, session, scheduler, null);
    }

    private ChatRunInstance newInstance(
            ChatRunEntity run,
            ChatSessionEntity session,
            ScheduledExecutorService scheduler,
            AgentExecutionAdapter agentExecution) {
        return new ChatRunInstance(
                runService,
                eventStore,
                properties,
                scheduler,
                workspaceAuditRecorder,
                run,
                session,
                agentExecution,
                new ChatRunSnapshotAccumulator(ChatRunSnapshotCodec.decode(run.getSnapshotJson())));
    }

    /**
     * 获取运行时租户标识。
     *
     * @param session 会话实体
     * @return 租户标识；会话未指定时返回 {@code default}
     */
    static String tenantId(ChatSessionEntity session) {
        return StringUtils.defaultIfBlank(session.getTenantId(), "default");
    }
}
