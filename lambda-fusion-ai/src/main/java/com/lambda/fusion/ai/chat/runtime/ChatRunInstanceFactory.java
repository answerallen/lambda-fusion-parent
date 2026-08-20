package com.lambda.fusion.ai.chat.runtime;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.runtime.adapter.AgentExecutionAdapter;
import com.lambda.fusion.ai.chat.runtime.event.ChatRunEventStore;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshotCodec;
import com.lambda.fusion.ai.chat.service.ChatRunStateService;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.gateway.FusionSubagentGateway;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 执行实例工厂：统一恢复/构造 {@link ChatRunInstance} 并承载所需静态小工具。恢复与终结共用同一套装配——带 Agent
 * 的实例用于执行与确认恢复，无 Agent 的纯落库实例仅用于终结；构造依赖集中在工厂，调度器由协调器按次传入，
 * 活动实例注册表始终由协调器独占，工厂不持有、查询或修改。
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
    private final ObjectProvider<FusionSubagentGateway> subagentGatewayProvider;
    private final AiProperties properties;

    /** 恢复带 Agent 的完整执行实例。 */
    ChatRunInstance restoreExecution(ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        eventStore.initialize(run.getId(), ChatRunSupport.sequenceFallback(run));
        return newInstance(run, session, scheduler, createAgentExecution(run, session));
    }

    /** 验证持久化 AgentState 中的 ASKING 工具与 Run 展示快照一致，避免重启后保留不可确认的僵尸运行。 */
    boolean hasRecoverableConfirmation(ChatRunEntity run, ChatSessionEntity session) {
        ExecutionSnapshot snapshot = ExecutionSnapshotCodec.decode(run.getSnapshotJson());
        Set<String> snapshotIds = new HashSet<>();
        for (var tool : snapshot.pendingTools()) {
            if (!snapshotIds.add(tool.toolCallId())) {
                return false;
            }
        }
        if (snapshotIds.isEmpty()) {
            return false;
        }
        List<ToolUseBlock> asking = createAgentExecution(run, session).readAskingToolBlocks();
        Set<String> askingIds = new HashSet<>();
        for (var tool : asking) {
            if (!askingIds.add(tool.getId())) {
                return false;
            }
        }
        return snapshotIds.equals(askingIds);
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

    /** 恢复无 Agent 的纯终结实例（仅用于落终态，不闭合 Agent 状态）。 */
    ChatRunInstance restoreFinalizer(ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        eventStore.initialize(run.getId(), ChatRunSupport.sequenceFallback(run));
        return newInstance(run, session, scheduler, null);
    }

    /** 终结前的恢复：优先恢复带 Agent 的实例以闭合未决工具调用，Agent 恢复失败时退化为纯落终态。 */
    ChatRunInstance restoreForFinalize(
            ChatRunEntity run, ChatSessionEntity session, ScheduledExecutorService scheduler) {
        try {
            return restoreExecution(run, session, scheduler);
        } catch (RuntimeException restoreFailure) {
            log.warn("终结前Agent恢复失败，仅落终态: runId={}", run.getId(), restoreFailure);
            return restoreFinalizer(run, session, scheduler);
        }
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
