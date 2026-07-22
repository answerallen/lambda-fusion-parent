package com.lambda.fusion.ai.chat.service.impl;

import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatService;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import com.lambda.fusion.ai.runtime.AgentFactory;
import com.lambda.fusion.ai.runtime.gateway.RuntimeProperty;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.gateway.HarnessGateway;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.OutboundAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * 对话流式服务实现。
 *
 * <p>流程：加载会话 -> 持久化用户消息 -> 构建/复用 Agent -> 经 {@link HarnessGateway#runStream} 订阅事件流 ->
 * 映射为 SSE 帧输出 -> 完成时持久化助手回复。Gateway 未启用时回退直连 {@code agent.streamEvents}。
 *
 * <p>多轮上下文由 Agent 内存状态（按 sessionId 隔离）维持；每次仅传入新用户消息。
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
    private final AgentFactory agentFactory;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;
    private final ObjectProvider<HarnessGateway> gatewayProvider;

    @Override
    public SseEmitter streamChat(String sessionId, String content) {
        ChatSessionEntity session = chatSessionService.loadOwned(sessionId);
        chatMessageService.saveUserMessage(session, content);
        HarnessAgent agent = agentFactory.getOrBuild(session.getAppId(), session.getTenantId());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        StringBuilder assistantText = new StringBuilder();
        long turnStartMillis = System.currentTimeMillis();
        routeStream(session, agent, content)
                .subscribe(
                        event -> {
                            try {
                                sendEvent(event, emitter, assistantText);
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        () -> {
                            try {
                                chatMessageService.saveAssistantMessage(session, assistantText.toString());
                                chatSessionService.touchLastMessageAt(session.getId());
                                workspaceAuditRecorder.recordChanges(session, turnStartMillis);
                            } catch (Exception e) {
                                log.error("持久化助手回复失败: sessionId={}", sessionId, e);
                            } finally {
                                emitter.complete();
                            }
                        });
        return emitter;
    }

    /**
     * Gateway 可用时统一走 runStream；否则直接调用 agent.streamEvents。
     */
    private Flux<AgentEvent> routeStream(ChatSessionEntity session, HarnessAgent agent, String content) {
        HarnessGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            io.agentscope.core.agent.RuntimeContext ctx = io.agentscope.core.agent.RuntimeContext.builder()
                    .userId(session.getUserId())
                    .sessionId(session.getId())
                    .put(RuntimeProperty.KEY_TENANT_ID, session.getTenantId())
                    .build();
            return agent.streamEvents(content, ctx);
        }
        MsgContext msgCtx = new MsgContext(
                CHANNEL_ID,
                session.getTenantId(),
                session.getId(),
                null,
                null,
                buildExtra(session, agent.getAgentId()),
                session.getUserId());
        OutboundAddress outbound = OutboundAddress.direct(CHANNEL_ID, CHANNEL_ID + ":DIRECT:" + session.getId());
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(content).build();
        return gateway.runStream(msgCtx, List.of(userMsg), outbound);
    }

    /**
     *
     * {@link MsgContext} 构造时 {@code Map.copyOf} 拒绝 null，故 tenantId 为空时省略
     */
    private static Map<String, String> buildExtra(ChatSessionEntity session, String agentId) {
        Map<String, String> extra = new HashMap<>();
        extra.put("agentId", agentId);
        extra.put(RuntimeProperty.KEY_APP_ID, session.getAppId());
        extra.put(RuntimeProperty.KEY_LF_SESSION_ID, session.getId());
        if (session.getTenantId() != null) {
            extra.put(RuntimeProperty.KEY_TENANT_ID, session.getTenantId());
        }
        return extra;
    }

    private void sendEvent(AgentEvent event, SseEmitter emitter, StringBuilder acc) throws Exception {
        AgentEventType type = event.getType();
        switch (type) {
            case TEXT_BLOCK_DELTA -> {
                if (event instanceof TextBlockDeltaEvent delta) {
                    acc.append(delta.getDelta());
                    emitter.send(SseEmitter.event().name("delta").data(delta.getDelta()));
                }
            }
            case TOOL_CALL_START ->
                emitter.send(SseEmitter.event().name("tool_start").data(""));
            case TOOL_CALL_END ->
                emitter.send(SseEmitter.event().name("tool_end").data(""));
            case AGENT_END -> emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            default -> {}
        }
    }
}
