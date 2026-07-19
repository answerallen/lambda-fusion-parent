package com.lambda.fusion.ai.agent.runtime;

import com.lambda.cloud.sse.SseEmitterManager;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.HintBlockEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AgentScope 事件流 -> SSE 桥接器（唯一边界适配）。
 *
 * <p>订阅 {@code Flux<AgentEvent>}，按 {@link AgentEventType} 映射为现有 {@link SseEmitterManager} 事件，
 * 沿用 {@code ChatMessageServiceImpl} 的 {@code clientId = "chat_" + sessionId} 与事件名约定
 * （{@code message}/{@code tool}/{@code hitl}/{@code error}）。聚合全文与最终 usage，返回
 * {@link AgentRunOutcome} 供调用方（Phase 2 重接的 {@code ChatMessageServiceImpl}）持久化后发送 {@code finish}。
 *
 * <p>spike 已核实事件访问器：{@link TextBlockDeltaEvent#getDelta()}、{@link AgentResultEvent#getResult()}、
 * {@link ToolCallStartEvent#getToolCallName()}、{@link ToolResultEndEvent#getState()}、{@link HintBlockEvent#getHint()}、
 * {@link ChatUsage#getInputTokens()}/{@code getOutputTokens()}。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventToSseAdapter {

    private final SseEmitterManager sseEmitterManager;

    /**
     * 桥接事件流到 SSE，流完成后返回聚合结果。
     *
     * @param clientId SSE 客户端标识（{@code "chat_" + sessionId}）
     * @param events   AgentScope 事件流
     * @return 聚合的运行结果（全文 + 最终消息 + token 用量）
     */
    public Mono<AgentRunOutcome> bridge(String clientId, Flux<AgentEvent> events) {
        StringBuilder answer = new StringBuilder();
        Accumulator acc = new Accumulator();
        return events.doOnNext(event -> dispatch(clientId, event, answer, acc))
                .doOnError(err -> {
                    log.error("AgentScope 事件流异常, clientId={}", clientId, err);
                    sseEmitterManager.sendEvent(clientId, "error", "系统异常，请稍后重试");
                })
                .then(Mono.fromSupplier(() -> {
                    String fullAnswer = answer.toString();
                    if (fullAnswer.isEmpty() && acc.result != null) {
                        fullAnswer = acc.result.getTextContent() != null ? acc.result.getTextContent() : "";
                    }
                    return new AgentRunOutcome(fullAnswer, acc.result, acc.inputTokens, acc.outputTokens);
                }));
    }

    private void dispatch(String clientId, AgentEvent event, StringBuilder answer, Accumulator acc) {
        AgentEventType type = event.getType();
        switch (type) {
            case TEXT_BLOCK_DELTA -> {
                String delta = ((TextBlockDeltaEvent) event).getDelta();
                if (delta != null && !delta.isEmpty()) {
                    answer.append(delta);
                    sseEmitterManager.sendEvent(clientId, "message", delta);
                }
            }
            case AGENT_RESULT -> {
                Msg result = ((AgentResultEvent) event).getResult();
                acc.result = result;
                if (result != null) {
                    ChatUsage usage = result.getChatUsage();
                    if (usage != null) {
                        acc.inputTokens = usage.getInputTokens();
                        acc.outputTokens = usage.getOutputTokens();
                    }
                }
            }
            case TOOL_CALL_START -> {
                ToolCallStartEvent e = (ToolCallStartEvent) event;
                sseEmitterManager.sendEvent(clientId, "tool", toolCallPayload(e.getToolCallName(), e.getToolCallId()));
            }
            case TOOL_RESULT_END -> {
                ToolResultEndEvent e = (ToolResultEndEvent) event;
                sseEmitterManager.sendEvent(
                        clientId, "tool_result", toolResultPayload(e.getToolCallName(), String.valueOf(e.getState())));
            }
            case HINT_BLOCK -> {
                HintBlockEvent e = (HintBlockEvent) event;
                sseEmitterManager.sendEvent(clientId, "hitl", e.getHint());
            }
            case EXCEED_MAX_ITERS -> sseEmitterManager.sendEvent(clientId, "error", "已达最大迭代次数，停止执行");
            case ALL_TOOLS_DENIED -> sseEmitterManager.sendEvent(clientId, "error", "所有工具被拒绝，停止执行");
            default -> {
                // 其余事件类型（MODEL_CALL_*/TEXT_BLOCK_START/END/THINKING_*/DATA_*/SUBAGENT_EXPOSED 等）
                // 暂不单独推送 SSE；Phase 2/4 按前端渲染需要再细化。
            }
        }
    }

    private static Map<String, String> toolCallPayload(String name, String callId) {
        Map<String, String> payload = new LinkedHashMap<>(2);
        payload.put("name", name);
        payload.put("callId", callId);
        return payload;
    }

    private static Map<String, String> toolResultPayload(String name, String state) {
        Map<String, String> payload = new LinkedHashMap<>(2);
        payload.put("name", name);
        payload.put("state", state);
        return payload;
    }

    /** 流式聚合过程中的可变累加器。 */
    private static final class Accumulator {
        private Msg result;
        private int inputTokens;
        private int outputTokens;
    }

    /** AgentScope 一次运行的聚合结果，供调用方持久化消息与记账。 */
    public record AgentRunOutcome(String answer, Msg result, int inputTokens, int outputTokens) {}
}
