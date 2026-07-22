package com.lambda.fusion.ai.rag.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.harness.agent.middleware.HarnessRuntimeMiddleware;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

/**
 * RAG 检索注入中间件：对话调用前按应用绑定的知识库检索相关片段，
 * 命中时以合成 USER 消息（{@link Msg#METADATA_SYNTHETIC}，不落 memory）追加到输入尾部。
 *
 * <p>只覆写 {@link #onAgent}（每次 call 检索一次，语义对齐旧 {@code GenericRAGHook} 的
 * PreCallEvent）；不用 {@code onReasoning}，避免 ReAct 每轮对同一 query 重复检索。
 * 检索失败/未命中一律原样放行，降级不阻断对话。
 *
 * <p>实现 {@link HarnessRuntimeMiddleware}（框架资产标记接口），agent 序列化复制时不被携带。
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
public class RagMiddleware implements HarnessRuntimeMiddleware {

    private final KnowledgeRetriever retriever;
    private final List<String> kbIds;
    private final int maxInjectChars;

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent, RuntimeContext context, AgentInput input, Function<AgentInput, Flux<AgentEvent>> next) {
        String query = lastUserQuery(input.msgs());
        if (StringUtils.isBlank(query) || kbIds.isEmpty()) {
            return next.apply(input);
        }
        return retriever
                .retrieve(kbIds, query)
                .flatMapMany(chunks -> {
                    if (chunks.isEmpty()) {
                        return next.apply(input);
                    }
                    List<Msg> enhanced = new ArrayList<>(input.msgs());
                    enhanced.add(buildKnowledgeMsg(chunks));
                    return next.apply(new AgentInput(enhanced));
                })
                .onErrorResume(e -> {
                    log.warn("知识库检索注入失败，降级放行原始输入: {}", e.getMessage());
                    return next.apply(input);
                });
    }

    private static String lastUserQuery(List<Msg> msgs) {
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Msg msg = msgs.get(i);
            if (msg.getRole() == MsgRole.USER) {
                return msg.getTextContent();
            }
        }
        return null;
    }

    // 拼接注入消息，沿用 GenericRAGHook 的 <retrieved_knowledge> Score/Content 格式，总长受 maxInjectChars 截断
    private Msg buildKnowledgeMsg(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder("<retrieved_knowledge>");
        sb.append("Use the following content from the knowledge base(s) if it is helpful:\n\n");
        for (RetrievedChunk chunk : chunks) {
            String entry = String.format("- Score: %.3f, Content: %s\n", chunk.score(), chunk.content());
            if (sb.length() + entry.length() > maxInjectChars) {
                break;
            }
            sb.append(entry);
        }
        sb.append("</retrieved_knowledge>");
        return Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(sb.toString()).build())
                .metadata(Map.<String, Object>of(Msg.METADATA_SYNTHETIC, true))
                .build();
    }
}
