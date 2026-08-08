package com.lambda.fusion.ai.chat.service.impl;

import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.adapter.AguiEventMapper;
import com.lambda.fusion.ai.chat.attachment.ChatAttachmentMessageBuilder;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatService;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.gateway.RuntimeProperty;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import com.lambda.fusion.core.utils.AuthUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * 对话流式服务实现。
 *
 * <p>流程：加载会话 -> 持久化用户消息 -> 构建/复用 Agent -> 经 {@link HarnessGateway#runStream} 订阅事件流 ->
 * 通过 {@link AguiEventMapper} 映射为 AG-UI 协议事件输出 -> 完成时持久化助手回复。Gateway 未启用时回退
 * 直连 {@code agent.streamEvents}。
 *
 * <p>多轮上下文由 Agent 内存状态（按 sessionId 隔离）维持；每次仅传入新用户消息。
 *
 * <p>HITL：工具标 {@code @RequireConfirm} 时，agent 调用前发 {@code RequireUserConfirmEvent} 暂停。
 * 本服务映射为 AG-UI {@code RunFinished(interrupt)} 并缓存待确认 ToolUseBlock；用户经
 * {@code POST /sessions/{id}/confirm} 回传决策，{@link #streamConfirm} 携带
 * {@code Msg.METADATA_CONFIRM_RESULTS} 恢复执行（同会话命中 ASKING 状态）。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChatServiceImpl implements ChatService {

    /**
     * SSE 连接超时（5 分钟），超时后 SseEmitter 抛 AsyncRequestTimeoutException -> 503。
     */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private static final String CHANNEL_ID = "fusion-chat";

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatAttachmentService chatAttachmentService;
    private final ChatAttachmentMessageBuilder attachmentMessageBuilder;
    private final AppService appService;
    private final AgentFactory agentFactory;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final ObjectProvider<HarnessGateway> gatewayProvider;

    /**
     * HITL 待确认工具调用缓存（sessionId -> ASK 时的 ToolUseBlock 列表）。
     *
     * <p>ASK 触发时缓存，回传端点 {@link #streamConfirm} 取出构造 ConfirmResult 恢复。
     * 单会话同一时刻最多一个待确认回合；回传时 remove 清空。未恢复即开新回合会覆盖
     * （旧 ASKING 状态仍留在 agent stateCache，需开新会话或清状态解除，属已知限制）。
     */
    private final Map<String, List<ToolUseBlock>> pendingConfirmations = new ConcurrentHashMap<>();

    @Override
    public SseEmitter streamChat(String sessionId, SendMessage message) {
        ChatSessionEntity session = chatSessionService.loadOwned(sessionId);
        AppEntity app = appService.loadById(session.getAppId());
        ChatMessageEntity userMessage =
                chatMessageService.saveUserMessage(session, StringUtils.defaultString(message.getContent()));
        List<ChatAttachmentEntity> attachments = message.getAttachmentIds() == null
                ? List.of()
                : chatAttachmentService.bindToMessage(session, message.getAttachmentIds(), userMessage.getId());
        Msg userMsg = attachmentMessageBuilder.buildUserMsg(session, app, message.getContent(), attachments);
        HarnessAgent agent = agentFactory.getOrBuild(session.getAppId(), AuthUtils.getTenantId());
        return runStream(sessionId, session, agent, userMsg);
    }

    @Override
    public SseEmitter streamConfirm(String sessionId, ConfirmToolCall dto) {
        ChatSessionEntity session = chatSessionService.loadOwned(sessionId);
        HarnessAgent agent = agentFactory.getOrBuild(session.getAppId(), AuthUtils.getTenantId());
        // applyConfirmResults 需完整 ToolUseBlock：confirmed=true 用其 withState(ALLOWED) 替换
        // ASKING 块，confirmed=false 用其 name 构造 DENIED 结果。故 ASK 时缓存原始块，此处按
        // toolCallId 取出（前端从 RunFinished interrupt 拿 toolCallId 回传）
        List<ToolUseBlock> pending = pendingConfirmations.remove(sessionId);
        if (pending == null || pending.isEmpty()) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "该会话无待确认工具调用");
        }
        List<ConfirmResult> results = new ArrayList<>();
        for (ConfirmToolCall.Decision decision : dto.getDecisions()) {
            String toolCallId = decision.getToolCallId();
            ToolUseBlock target = pending.stream()
                    .filter(t -> t.getId().equals(toolCallId))
                    .findFirst()
                    .orElseThrow(
                            () -> new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "待确认工具调用不存在: " + toolCallId));
            results.add(new ConfirmResult(decision.isConfirmed(), target));
        }
        Msg confirmMsg = UserMessage.builder()
                .metadata(Map.<String, Object>of(Msg.METADATA_CONFIRM_RESULTS, results))
                .build();
        return runStream(sessionId, session, agent, confirmMsg);
    }

    /**
     * SSE 事件流编排：订阅 Agent 事件 -> 映射 AG-UI 输出 -> 区分正常结束/HITL 暂停/异常。
     *
     * <p>三种结束路径（{@code completed} 标志保证仅走一条，Flux#onComplete 兜底时若已处理则仅关连接）：
     * <ul>
     *   <li>{@code AGENT_END}：正常结束，持久化助手回复 + 关连接。</li>
     *   <li>{@code REQUIRE_USER_CONFIRM}：HITL 暂停，缓存待确认 ToolUseBlock，发 RunFinished(interrupt)
     *       后关连接（不持久化），agent 状态保留 ASKING 等回传端点恢复。</li>
     *   <li>异常：发 RunError 后关连接。</li>
     * </ul>
     */
    private SseEmitter runStream(String sessionId, ChatSessionEntity session, HarnessAgent agent, Msg msg) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        StringBuilder assistantText = new StringBuilder();
        String runId = UUID.randomUUID().toString();
        // enableReasoning=true：默认输出推理过程，前端用 reasoning 折叠区渲染
        AguiEventMapper mapper = new AguiEventMapper(session.getId(), runId, true);
        long turnStartMillis = System.currentTimeMillis();
        AtomicBoolean completed = new AtomicBoolean(false);
        Disposable disposable = routeStream(session, agent, msg)
                .subscribe(
                        event -> {
                            try {
                                // 累积文本增量用于持久化助手回复（工具调用由 mapper 累积，流结束后单独落库 tool_call）
                                if (event instanceof TextBlockDeltaEvent delta) {
                                    assistantText.append(delta.getDelta());
                                }
                                for (AguiEvent agui : mapper.map(event)) {
                                    emitter.send(SseEmitter.event().data(mapper.encodeToJson(agui)));
                                }
                                if (event.getType() == AgentEventType.REQUIRE_USER_CONFIRM
                                        && completed.compareAndSet(false, true)) {
                                    // HITL 暂停：缓存待确认 ToolUseBlock 供回传端点恢复，不持久化
                                    if (event instanceof RequireUserConfirmEvent ruc) {
                                        pendingConfirmations.put(sessionId, ruc.getToolCalls());
                                    }
                                } else if (event.getType() == AgentEventType.AGENT_END
                                        && completed.compareAndSet(false, true)) {
                                    persistAndComplete(
                                            sessionId, session, mapper, assistantText, emitter, turnStartMillis);
                                }
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            // 发 RunError 后正常关闭连接（非 completeWithError）：前端 onRunError
                            // 拿到错误事件显示提示，onComplete 让对话状态结束可重试
                            if (completed.compareAndSet(false, true)) {
                                try {
                                    for (AguiEvent agui : mapper.mapError(error)) {
                                        emitter.send(SseEmitter.event().data(mapper.encodeToJson(agui)));
                                    }
                                } catch (Exception sendError) {
                                    log.warn("发送 RunError 失败: sessionId={}", sessionId, sendError);
                                }
                                log.error("对话流异常: sessionId={}", sessionId, error);
                                emitter.complete();
                            }
                        },
                        () -> {
                            if (completed.compareAndSet(false, true)) {
                                persistAndComplete(sessionId, session, mapper, assistantText, emitter, turnStartMillis);
                            } else {
                                emitter.complete();
                            }
                        });
        // emitter 结束（完成/超时）时取消 Flux 订阅，避免 gateway Flux 挂起泄漏
        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(disposable::dispose);
        return emitter;
    }

    private Flux<AgentEvent> routeStream(ChatSessionEntity session, HarnessAgent agent, Msg userMsg) {
        HarnessGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            RuntimeContext ctx = RuntimeContext.builder()
                    .userId(session.getUserId())
                    .sessionId(session.getId())
                    .put(RuntimeProperty.KEY_TENANT_ID, AuthUtils.getTenantId())
                    .build();
            return agent.streamEvents(userMsg, ctx);
        }

        String stableAgentId = agentFactory.buildStableAgentId(session.getAppId(), session.getTenantId());

        MsgContext msgCtx = new MsgContext(
                CHANNEL_ID,
                AuthUtils.getTenantId(),
                session.getId(),
                null,
                null,
                buildExtra(session, stableAgentId),
                session.getUserId());
        OutboundAddress outbound = OutboundAddress.direct(CHANNEL_ID, CHANNEL_ID + ":DIRECT:" + session.getId());
        return gateway.runStream(msgCtx, List.of(userMsg), outbound);
    }

    /**
     *
     * {@link MsgContext} 构造时 {@code Map.copyOf} 拒绝 null，故 tenantId 为空时省略
     */
    private static Map<String, String> buildExtra(ChatSessionEntity session, String agentId) {
        Map<String, String> extra = new HashMap<>();
        extra.put(RuntimeProperty.KEY_AGENT_ID, agentId);
        extra.put(RuntimeProperty.KEY_APP_ID, session.getAppId());
        extra.put(RuntimeProperty.KEY_LF_SESSION_ID, session.getId());
        extra.put(RuntimeProperty.KEY_TENANT_ID, AuthUtils.getTenantId());
        return extra;
    }

    /** 持久化助手回复（文本 + 工具调用）并关闭 SSE 连接。 */
    private void persistAndComplete(
            String sessionId,
            ChatSessionEntity session,
            AguiEventMapper mapper,
            StringBuilder assistantText,
            SseEmitter emitter,
            long turnStartMillis) {
        try {
            String toolCallJson = serializeToolCalls(mapper.getToolCalls());
            chatMessageService.saveAssistantMessage(session, assistantText.toString(), toolCallJson);
            chatSessionService.touchLastMessageAt(session.getId());
            workspaceAuditRecorder.recordChanges(session, turnStartMillis);
        } catch (Exception e) {
            log.error("持久化助手回复失败: sessionId={}", sessionId, e);
        } finally {
            emitter.complete();
        }
    }

    /** 序列化工具调用快照为 JSON（空列表返回 null，不写 tool_call 字段）。 */
    private static String serializeToolCalls(List<AguiEventMapper.ToolCallRecord> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        return JsonUtils.getJsonCodec().toJson(toolCalls);
    }
}
