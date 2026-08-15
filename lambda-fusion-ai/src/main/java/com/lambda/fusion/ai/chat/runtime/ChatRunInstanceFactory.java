package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.adapter.AgentExecutionAdapter;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 执行实例工厂：统一恢复/构造 {@link ChatRunInstance}，并承载其所需的静态小工具。
 *
 * <p>恢复与终结共用同一套实例装配：带 Agent 的完整实例用于执行与确认恢复，无 Agent 的纯落库实例
 * 仅用于终结。构造依赖（事件存储、Agent 工厂、审计、网关、配置）集中在工厂，调度器与活动实例注册表
 * 由调用方按次传入（属协调器状态）。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ChatRunInstanceFactory {

    private final ChatRunStateService runService;
    private final ChatRunEventStore eventStore;
    private final AgentFactory agentFactory;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final ObjectProvider<HarnessGateway> gatewayProvider;
    private final AiProperties properties;

    /** 恢复带 Agent 的完整执行实例（未注册进活动实例集合，注册由调用方决定）。 */
    ChatRunInstance restoreExecution(
            ChatRunEntity run,
            ChatSessionEntity session,
            ScheduledExecutorService scheduler,
            ConcurrentMap<String, ChatRunInstance> executions) {
        eventStore.initialize(run.getId(), ChatRunSupport.sequenceFallback(run));
        String tenantId = tenantId(session);
        HarnessAgent agent = agentFactory.getOrBuild(session.getAppId(), tenantId);
        AgentExecutionAdapter agentExecution = new AgentExecutionAdapter(
                agent,
                gatewayProvider.getIfAvailable(),
                agentFactory.buildStableAgentId(session.getAppId(), tenantId),
                run,
                session,
                tenantId);
        return newInstance(run, session, scheduler, executions, agentExecution);
    }

    /** 恢复无 Agent 的纯终结实例（仅用于落终态，不闭合 Agent 状态）。 */
    ChatRunInstance restoreFinalizer(
            ChatRunEntity run,
            ChatSessionEntity session,
            ScheduledExecutorService scheduler,
            ConcurrentMap<String, ChatRunInstance> executions) {
        eventStore.initialize(run.getId(), ChatRunSupport.sequenceFallback(run));
        return newInstance(run, session, scheduler, executions, null);
    }

    /** 终结前的恢复：优先恢复带 Agent 的实例以闭合未决工具调用，Agent 恢复失败时退化为纯落终态。 */
    ChatRunInstance restoreForFinalize(
            ChatRunEntity run,
            ChatSessionEntity session,
            ScheduledExecutorService scheduler,
            ConcurrentMap<String, ChatRunInstance> executions) {
        try {
            return restoreExecution(run, session, scheduler, executions);
        } catch (RuntimeException restoreFailure) {
            log.warn("终结前Agent恢复失败，仅落终态: runId={}", run.getId(), restoreFailure);
            return restoreFinalizer(run, session, scheduler, executions);
        }
    }

    /** 查询活动实例；不存在时恢复一个并注册（并发竞争下取先注册者）。 */
    ChatRunInstance selectOrRestore(
            ChatRunEntity run,
            ChatSessionEntity session,
            ScheduledExecutorService scheduler,
            ConcurrentMap<String, ChatRunInstance> executions) {
        ChatRunInstance selected = executions.get(run.getId());
        if (selected != null) {
            return selected;
        }
        ChatRunInstance candidate = restoreExecution(run, session, scheduler, executions);
        ChatRunInstance existing = executions.putIfAbsent(run.getId(), candidate);
        return existing == null ? candidate : existing;
    }

    private ChatRunInstance newInstance(
            ChatRunEntity run,
            ChatSessionEntity session,
            ScheduledExecutorService scheduler,
            ConcurrentMap<String, ChatRunInstance> executions,
            AgentExecutionAdapter agentExecution) {
        return new ChatRunInstance(
                runService,
                eventStore,
                properties,
                scheduler,
                workspaceAuditRecorder,
                executions,
                run,
                session,
                agentExecution,
                new ChatRunSnapshotAccumulator(ExecutionSnapshotCodec.decode(run.getSnapshotJson())));
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
