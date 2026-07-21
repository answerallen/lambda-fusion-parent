package com.lambda.fusion.ai.chat.service.impl;

import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatService;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import com.lambda.fusion.ai.runtime.AiAgentFactory;
import com.lambda.fusion.ai.runtime.workspace.WorkspaceAuditRecorder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话流式服务实现。
 *
 * <p>流程：加载会话 -> 持久化用户消息 -> 构建/复用 Agent -> 订阅 {@code streamEvents} 事件流 ->
 * 映射为 SSE 帧输出 -> 完成时持久化助手回复。
 *
 * <p>多轮上下文由 Agent 的内存状态（按 sessionId 隔离）维持；每次仅传入新用户消息。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChatServiceImpl implements ChatService {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final AiAgentFactory aiAgentFactory;
    private final WorkspaceAuditRecorder workspaceAuditRecorder;

    @Override
    public SseEmitter streamChat(String sessionId, String content) {
        ChatSessionEntity session = chatSessionService.loadOwned(sessionId);
        chatMessageService.saveUserMessage(session, content);
        HarnessAgent agent = aiAgentFactory.getOrBuild(session.getAppId(), session.getTenantId());
        RuntimeContext ctx = RuntimeContext.builder()
                .userId(session.getUserId())
                .sessionId(session.getId())
                .put("tenantId", session.getTenantId())
                .build();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        StringBuilder assistantText = new StringBuilder();
        long turnStartMillis = System.currentTimeMillis();
        agent.streamEvents(content, ctx)
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
            default -> {
                // 其他事件类型暂不向前端推送
            }
        }
    }
}
