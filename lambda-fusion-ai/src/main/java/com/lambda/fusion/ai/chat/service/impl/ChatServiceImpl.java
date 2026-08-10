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
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.*;
import io.agentscope.core.message.*;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对话流式服务实现。Gateway 启用时经 {@link HarnessGateway#runStream} 订阅，未启用时回退直连
 * {@code agent.streamEvents}；事件经 {@link AguiEventMapper} 映射为 AG-UI 协议输出。HITL 待确认
 * 工具调用持久化于 {@code pending_confirm}，由 {@link #streamConfirm} 回传恢复。
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

    /** HITL 待确认工具调用内存缓存（sessionId -> ToolUseBlock 列表），confirm 回传时清空。 */
    private final Map<String, List<ToolUseBlock>> pendingConfirmations = new ConcurrentHashMap<>();

    @Override
    public SseEmitter streamChat(String sessionId, SendMessage message) {
        ChatSessionEntity session = chatSessionService.loadOwned(sessionId);
        AppEntity app = appService.loadById(session.getAppId());
        HarnessAgent agent = agentFactory.getOrBuild(session.getAppId(), AuthUtils.getTenantId());

        // HITL 残留：上轮 ASK 未确认则重发 interrupt，否则 ASKING 无 ConfirmResult 会抛异常锁死会话。
        List<ToolUseBlock> asking = deserializePendingConfirm(session.getPendingConfirm());
        String source = !asking.isEmpty() ? "db" : null;
        if (asking.isEmpty()) {
            asking = pendingConfirmations.get(sessionId);
            if (asking != null && !asking.isEmpty()) {
                source = "pendingCache";
            }
        }
        if (asking == null || asking.isEmpty()) {
            asking = findAskingToolUseBlocks(agent, session);
            if (!asking.isEmpty()) {
                source = "agentState";
            }
        }
        if (!asking.isEmpty()) {
            pendingConfirmations.put(sessionId, asking);
            // DB 无记录时回写
            if (session.getPendingConfirm() == null) {
                chatSessionService.updatePendingConfirm(sessionId, serializePendingConfirm(asking));
            }
            log.info(
                    "检测到 HITL 待确认，重发确认请求: sessionId={}, source={}, tools={}",
                    sessionId,
                    source,
                    asking.stream().map(ToolUseBlock::getName).toList());
            return emitInterrupt(sessionId, asking);
        }

        ChatMessageEntity userMessage =
                chatMessageService.saveUserMessage(session, StringUtils.defaultString(message.getContent()));
        List<ChatAttachmentEntity> attachments = message.getAttachmentIds() == null
                ? List.of()
                : chatAttachmentService.bindToMessage(session, message.getAttachmentIds(), userMessage.getId());
        Msg userMsg = attachmentMessageBuilder.buildUserMsg(session, app, message.getContent(), attachments);
        return runStream(sessionId, session, agent, userMsg);
    }

    @Override
    public SseEmitter streamConfirm(String sessionId, ConfirmToolCall dto) {
        ChatSessionEntity session = chatSessionService.loadOwned(sessionId);
        HarnessAgent agent = agentFactory.getOrBuild(session.getAppId(), AuthUtils.getTenantId());
        // applyConfirmResults 需完整 ToolUseBlock（withState(ALLOWED) 替换 / name 构造 DENIED），
        // 依次从 pending_confirm、内存、agent 状态取。
        List<ToolUseBlock> pending = deserializePendingConfirm(session.getPendingConfirm());
        String pendingSource = !pending.isEmpty() ? "db" : null;
        // 无论 DB 是否命中，都清内存缓存（避免残留）
        List<ToolUseBlock> memoryPending = pendingConfirmations.remove(sessionId);
        if (pending.isEmpty() && memoryPending != null && !memoryPending.isEmpty()) {
            pending = memoryPending;
            pendingSource = "memory";
        }
        if (pending.isEmpty()) {
            pending = findAskingToolUseBlocks(agent, session);
            pendingSource = "stateStore";
        }
        if (pending.isEmpty()) {
            log.warn("confirm 流无待确认工具调用: sessionId={}", sessionId);
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "该会话无待确认工具调用");
        }
        log.info(
                "confirm 流开始: sessionId={}, pendingSource={}, decisions={}",
                sessionId,
                pendingSource,
                dto.getDecisions());
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
     * 提取 ASKING 状态的 ToolUseBlock（与 {@code askingToolCalls()} 同逻辑）。
     * 读 stateCache 与 stateStore 取并集，作直连模式/极端情况回退。
     */
    private List<ToolUseBlock> findAskingToolUseBlocks(HarnessAgent agent, ChatSessionEntity session) {
        ReActAgent delegate = agent.getDelegate();
        List<ToolUseBlock> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            for (AgentState state :
                    new AgentState[] {loadStateFromStateCache(delegate, session), loadStateFromStore(delegate, session)
                    }) {
                for (ToolUseBlock block : extractAskingBlocks(state)) {
                    if (seen.add(block.getId())) {
                        result.add(block);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("检测 HITL ASKING 残留异常，返回已收集部分: sessionId={}", session.getId(), e);
        }
        return result;
    }

    /** 从一条 AgentState 的最后一条 assistant 消息提取 ASKING 工具调用块（与 askingToolCalls 同逻辑）。 */
    private static List<ToolUseBlock> extractAskingBlocks(AgentState state) {
        if (state == null || state.getContext() == null) {
            return List.of();
        }
        List<Msg> context = state.getContext();
        for (int i = context.size() - 1; i >= 0; i--) {
            Msg msg = context.get(i);
            if (msg.getRole() != MsgRole.ASSISTANT) {
                continue;
            }
            return msg.getContent().stream()
                    .filter(b -> b instanceof ToolUseBlock t && t.getState() == ToolCallState.ASKING)
                    .map(ToolUseBlock.class::cast)
                    .toList();
        }
        return List.of();
    }

    /** 读 stateCache（getAgentState，命中 slot 不触发 store 读）。 */
    private static AgentState loadStateFromStateCache(ReActAgent delegate, ChatSessionEntity session) {
        try {
            return delegate.getAgentState(session.getUserId(), session.getId());
        } catch (Exception e) {
            log.warn("读 stateCache 失败，容错跳过: sessionId={}", session.getId(), e);
            return null;
        }
    }

    /** 读 stateStore，与 activateSlotForContext 的 store.get 同 key。 */
    private static AgentState loadStateFromStore(ReActAgent delegate, ChatSessionEntity session) {
        try {
            AgentStateStore store = delegate.getStateStore();
            if (store == null) {
                return null;
            }
            return store.get(session.getUserId(), session.getId(), "agent_state", AgentState.class)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("读 stateStore 失败，容错跳过: sessionId={}", session.getId(), e);
            return null;
        }
    }

    /** 是否 agent HITL 暂停抛出的 IllegalStateException（遍历 cause 链匹配消息关键词）。 */
    private static boolean isHitlPauseError(Throwable error) {
        Throwable cur = error;
        while (cur != null) {
            String message = cur.getMessage();
            if (message != null
                    && (message.contains("human-in-the-loop confirmation")
                            || message.contains("are in ASKING state"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * 发送 HITL interrupt 事件序列（RunStarted + RunFinished(interrupt)），不调 agent.streamEvents。
     * 用于 ASKING 残留时重发确认卡片。
     */
    private SseEmitter emitInterrupt(String sessionId, List<ToolUseBlock> blocks) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String runId = UUID.randomUUID().toString();
        AguiEventMapper mapper = new AguiEventMapper(sessionId, runId, true);
        try {
            for (AguiEvent agui : mapper.buildInterruptEvents(blocks)) {
                emitter.send(SseEmitter.event().data(mapper.encodeToJson(agui)));
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * SSE 事件流编排：订阅 Agent 事件 -> 映射 AG-UI 输出 -> 区分正常结束/HITL 暂停/异常。
     * {@code completed} 标志保证三种结束路径（AGENT_END / REQUIRE_USER_CONFIRM / 异常）仅走一条。
     */
    private SseEmitter runStream(String sessionId, ChatSessionEntity session, HarnessAgent agent, Msg msg) {
        boolean isConfirm = msg.getMetadata() != null && msg.getMetadata().containsKey(Msg.METADATA_CONFIRM_RESULTS);
        String flow = isConfirm ? "confirm" : "chat";
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        StringBuilder assistantText = new StringBuilder();
        String runId = UUID.randomUUID().toString();
        // enableReasoning=true：默认输出推理过程，前端用 reasoning 折叠区渲染
        AguiEventMapper mapper = new AguiEventMapper(session.getId(), runId, true);
        long turnStartMillis = System.currentTimeMillis();
        AtomicBoolean completed = new AtomicBoolean(false);
        log.info("runStream[{}] 开始: sessionId={}", flow, sessionId);
        Disposable disposable = routeStream(session, agent, msg)
                .subscribe(
                        event -> {
                            try {
                                log.info("runStream[{}] 事件: type={}", flow, event.getType());
                                // 累积文本增量用于持久化助手回复（工具调用由 mapper 累积，流结束后单独落库 tool_call）
                                if (event instanceof TextBlockDeltaEvent delta) {
                                    assistantText.append(delta.getDelta());
                                }
                                for (AguiEvent agui : mapper.map(event)) {
                                    emitter.send(SseEmitter.event().data(mapper.encodeToJson(agui)));
                                }
                                if (event.getType() == AgentEventType.REQUIRE_USER_CONFIRM
                                        && completed.compareAndSet(false, true)) {
                                    // HITL 暂停：缓存并持久化待确认块到 pending_confirm（按 session.getId()，重启不丢）。
                                    log.info("runStream[{}] HITL 暂停(ASK): sessionId={}", flow, sessionId);
                                    if (event instanceof RequireUserConfirmEvent ruc) {
                                        List<ToolUseBlock> blocks = ruc.getToolCalls();
                                        pendingConfirmations.put(sessionId, blocks);
                                        chatSessionService.updatePendingConfirm(
                                                sessionId, serializePendingConfirm(blocks));
                                    }
                                } else if (event.getType() == AgentEventType.AGENT_END
                                        && completed.compareAndSet(false, true)) {
                                    log.info("runStream[{}] AGENT_END: sessionId={}", flow, sessionId);
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
                            if (!completed.compareAndSet(false, true)) {
                                return;
                            }
                            // HITL 兜底：残留检测漏检时 agent 抛 "paused for human-in-the-loop"，
                            // 取完整块重发 interrupt 避免锁死（applyConfirmResults 需完整块做 withState 替换）。
                            if (isHitlPauseError(error)) {
                                List<ToolUseBlock> asking = deserializePendingConfirm(session.getPendingConfirm());
                                if (asking == null || asking.isEmpty()) {
                                    asking = pendingConfirmations.get(sessionId);
                                }
                                if (asking == null || asking.isEmpty()) {
                                    asking = findAskingToolUseBlocks(agent, session);
                                }
                                if (!asking.isEmpty()) {
                                    log.warn(
                                            "runStream[{}] HITL 异常兜底转 interrupt: sessionId={}, tools={}",
                                            flow,
                                            sessionId,
                                            asking.stream()
                                                    .map(ToolUseBlock::getName)
                                                    .toList());
                                    pendingConfirmations.put(sessionId, asking);
                                    if (session.getPendingConfirm() == null) {
                                        chatSessionService.updatePendingConfirm(
                                                sessionId, serializePendingConfirm(asking));
                                    }
                                    try {
                                        for (AguiEvent agui : mapper.buildInterruptEvents(asking)) {
                                            emitter.send(SseEmitter.event().data(mapper.encodeToJson(agui)));
                                        }
                                    } catch (Exception sendError) {
                                        log.warn("发送 HITL interrupt 失败: sessionId={}", sessionId, sendError);
                                    }
                                    emitter.complete();
                                    return;
                                }
                            }
                            try {
                                for (AguiEvent agui : mapper.mapError(error)) {
                                    emitter.send(SseEmitter.event().data(mapper.encodeToJson(agui)));
                                }
                            } catch (Exception sendError) {
                                log.warn("发送 RunError 失败: sessionId={}", sessionId, sendError);
                            }
                            log.error("runStream[{}] 对话流异常: sessionId={}", flow, sessionId, error);
                            emitter.complete();
                        },
                        () -> {
                            log.info(
                                    "runStream[{}] Flux onComplete: sessionId={}, completed={}",
                                    flow,
                                    sessionId,
                                    completed.get());
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

    /** {@link MsgContext} 构造时 {@code Map.copyOf} 拒绝 null，故 tenantId 为空时省略 */
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
            // 回合结束，清空 HITL 待确认
            chatSessionService.updatePendingConfirm(session.getId(), null);
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

    /**
     * 序列化 HITL 待确认 ToolUseBlock 列表为 JSON（空返回 null）。包装为 {@code List<ContentBlock>}
     * 触发 {@code @JsonTypeInfo} 写 type 字段，保证 ToolUseBlock 往返。
     */
    private record PendingConfirmHolder(List<ContentBlock> blocks) {}

    private static String serializePendingConfirm(List<ToolUseBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        return JsonUtils.getJsonCodec().toJson(new PendingConfirmHolder(new ArrayList<>(blocks)));
    }

    private static List<ToolUseBlock> deserializePendingConfirm(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            PendingConfirmHolder holder = JsonUtils.getJsonCodec().fromJson(json, PendingConfirmHolder.class);
            if (holder == null || holder.blocks() == null) {
                return List.of();
            }
            return holder.blocks().stream()
                    .filter(b -> b instanceof ToolUseBlock)
                    .map(b -> (ToolUseBlock) b)
                    .toList();
        } catch (Exception e) {
            log.warn("反序列化 pendingConfirm 失败: sessionId json={}", json, e);
            return List.of();
        }
    }
}
