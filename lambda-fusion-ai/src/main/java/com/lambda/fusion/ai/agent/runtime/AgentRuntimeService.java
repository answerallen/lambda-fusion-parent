package com.lambda.fusion.ai.agent.runtime;

import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AgentScope 智能体运行时服务。
 *
 * <p>契约边界：
 * <ul>
 *   <li>{@link #run} 流式（聊天主路，session-centric）：按 session（{@code appId} -> AppEntity 模板 +
 *       执行参数快照）+ 请求 override 构造 {@code HarnessAgent}，返回事件流供聊天层桥接 SSE。</li>
 *   <li>{@link #call} 同步 one-shot（可选，app-centric）：无 session，按 appId 模板构造，聚合结果。</li>
 *   <li>{@link #resume} HITL 恢复（session-centric）：经 AgentScope 分布式会话按 sessionId 恢复，
 *       接续 {@code HintBlockEvent} 中断点。</li>
 * </ul>
 *
 * <p>运行时参数覆盖规则：{@code 请求参数（SendMessage override） > session 快照}；结构化配置
 * （{@code subagentSpec}/{@code toolGroups}/{@code mcpServerIds}/{@code middlewareConfig}）从 app 实时读。
 *
 * @author Jin
 */
public interface AgentRuntimeService {

    /** 流式执行（聊天主路）：返回 AgentScope 事件流，调用方经 {@link EventToSseAdapter} 桥接 SSE。 */
    Flux<AgentEvent> run(ChatSessionEntity session, SendMessage input);

    /** 同步 one-shot（app-centric，可选）：按 appId 构造 agent，聚合返回最终消息。 */
    Mono<Msg> call(String appId, String input);

    /** HITL 恢复（session-centric）：按 sessionId 恢复分布式会话并接续执行。 */
    Flux<AgentEvent> resume(ChatSessionEntity session, SendMessage input);
}
