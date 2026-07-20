package com.lambda.fusion.ai.agent.runtime;

import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 知识库检索工具（agentic @Tool）：per-agent 绑定多 KB，agent 自主调用 {@code retrieve_knowledge}
 * 跨 KB 检索 + 按 score 合并取 topK。非 Spring bean，由 {@link AgentRuntimeServiceImpl} 按 session kbIds 构造。
 *
 * <p>同步方法内 {@code block()} 的安全性：AgentScope {@code ToolExecutor} 将同步 @Tool 调用经
 * {@code Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())} 卸载到弹性线程执行
 * （2.0.0 字节码核实），此处的阻塞发生在 worker 线程而非事件循环。<b>升级 AgentScope 版本时需复核该行为。</b>
 *
 * @author Jin
 */
@Slf4j
public class KnowledgeRetrievalTool {

    private static final int DEFAULT_LIMIT = 5;

    private final List<SimpleKnowledge> knowledgeBases;

    private final KnowledgeRetriever knowledgeRetriever;

    private final int defaultLimit;

    public KnowledgeRetrievalTool(
            List<SimpleKnowledge> knowledgeBases, KnowledgeRetriever knowledgeRetriever, Integer defaultLimit) {
        this.knowledgeBases = knowledgeBases != null ? knowledgeBases : List.of();
        this.knowledgeRetriever = knowledgeRetriever;
        this.defaultLimit = defaultLimit != null && defaultLimit > 0 ? defaultLimit : DEFAULT_LIMIT;
    }

    /**
     * 检索知识库，返回与查询相关的文档片段（跨 KB 合并、按 score 降序取 topK）。
     *
     * @param query 检索查询文本
     * @param limit 返回结果数量上限（可选，默认取构造期 defaultLimit）
     * @return 格式化的检索结果文本
     */
    @Tool(
            name = "retrieve_knowledge",
            description = "检索知识库获取与问题相关的文档片段。当需要查询外部知识、文档或事实信息，或用户询问" + "存储的知识时调用。",
            readOnly = true)
    public String retrieveKnowledge(
            @ToolParam(name = "query", description = "检索查询文本，用于在知识库中查找相关文档") String query,
            @ToolParam(name = "limit", description = "返回结果数量上限（默认 5）", required = false) Integer limit) {
        if (knowledgeBases.isEmpty()) {
            return "知识库为空，无可检索内容。";
        }
        int topK = limit != null && limit > 0 ? limit : defaultLimit;
        List<Document> merged =
                knowledgeRetriever.retrieve(knowledgeBases, query, topK).block();
        return formatResults(merged);
    }

    private static String formatResults(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "未检索到相关内容。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("检索到 ").append(docs.size()).append(" 条相关内容：\n\n");
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            sb.append("[").append(i + 1).append("]");
            if (d.getScore() != null) {
                sb.append(" (score: ")
                        .append(String.format("%.3f", d.getScore()))
                        .append(")");
            }
            sb.append(":\n");
            String text = d.getMetadata() != null ? d.getMetadata().getContentText() : "";
            sb.append(text).append("\n\n");
        }
        return sb.toString();
    }
}
