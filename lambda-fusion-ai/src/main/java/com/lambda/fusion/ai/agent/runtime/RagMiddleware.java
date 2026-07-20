package com.lambda.fusion.ai.agent.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * RAG 检索中间件（static 模式）：{@link #onReasoning} 拦截，每次 reply 首次推理前用 user 消息检索，
 * 结果作 system context 注入 messages。仅首次检索（{@link RuntimeContext} 标记），后续推理轮交 agentic
 * {@link KnowledgeRetrievalTool}。
 *
 * <p>检索经 {@link KnowledgeRetriever} 全反应式组合（{@code Mono} 拼接 {@code next}，无阻塞调用）——
 * middleware 在 agent 反应式管线内执行，阻塞可能卡死事件线程。非 Spring bean，由
 * {@link AgentRuntimeServiceImpl} 按 session kbIds 构造。
 *
 * @author Jin
 */
@Slf4j
public class RagMiddleware implements MiddlewareBase {

    private static final String CTX_KEY_STATIC_DONE = "_rag_static_done";

    private final List<SimpleKnowledge> knowledgeBases;

    private final KnowledgeRetriever knowledgeRetriever;

    private final int topK;

    public RagMiddleware(List<SimpleKnowledge> knowledgeBases, KnowledgeRetriever knowledgeRetriever, Integer topK) {
        this.knowledgeBases = knowledgeBases != null ? knowledgeBases : List.of();
        this.knowledgeRetriever = knowledgeRetriever;
        this.topK = topK != null && topK > 0 ? topK : 5;
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent, RuntimeContext ctx, ReasoningInput input, Function<ReasoningInput, Flux<AgentEvent>> next) {
        if (knowledgeBases.isEmpty() || ctx.get(CTX_KEY_STATIC_DONE) != null) {
            return next.apply(input);
        }
        ctx.put(CTX_KEY_STATIC_DONE, true);
        String query = extractLastUserQuery(input.messages());
        if (query == null) {
            return next.apply(input);
        }
        return knowledgeRetriever.retrieve(knowledgeBases, query, topK).flatMapMany(docs -> {
            if (docs.isEmpty()) {
                return next.apply(input);
            }
            log.debug("RagMiddleware: static 检索注入，query={} hits={}", query, docs.size());
            return next.apply(augment(input, docs));
        });
    }

    /** 在 messages 头部注入一条检索上下文 SYSTEM 消息。 */
    private static ReasoningInput augment(ReasoningInput input, List<Document> docs) {
        Msg contextMsg = Msg.builder()
                .role(MsgRole.SYSTEM)
                .textContent(formatContext(docs))
                .build();
        List<Msg> augmented = new ArrayList<>(input.messages().size() + 1);
        augmented.add(contextMsg);
        augmented.addAll(input.messages());
        return new ReasoningInput(augmented, input.tools(), input.options());
    }

    private String extractLastUserQuery(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg m = messages.get(i);
            if (m.getRole() == MsgRole.USER) {
                String text = m.getTextContent();
                return text != null && !text.isBlank() ? text : null;
            }
        }
        return null;
    }

    private static String formatContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是检索到的相关知识，请结合这些资料回答用户问题：\n\n");
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            String text = d.getMetadata() != null ? d.getMetadata().getContentText() : "";
            sb.append("[").append(i + 1).append("] ").append(text).append("\n\n");
        }
        return sb.toString();
    }
}
