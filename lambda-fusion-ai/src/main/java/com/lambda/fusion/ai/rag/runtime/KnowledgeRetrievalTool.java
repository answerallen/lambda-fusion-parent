package com.lambda.fusion.ai.rag.runtime;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Agentic 模式知识检索工具：把应用绑定的知识库注册为 agent 的 {@code retrieve_knowledge}
 * 工具，由模型在 ReAct 循环中自主决定何时检索、检索什么。
 *
 * <p>kbIds 在 agent 构建期绑定（{@code AgentFactory.build} 按 app 注册，与 RagMiddleware
 * 同一「绑定即挂载 + ConfigChangedEvent 失效重建」生命周期），不走 ToolkitAssembler 的
 * 全局 Bean 扫描（扫描是全局共享语义，无法按 app 隔离检索范围）。
 *
 * <p>格式化沿用旧 {@code KnowledgeRetrievalTools} 的纯文本风格（Score + 内容）；
 * 空结果/空白 query/检索异常一律返回友好文案，绝不向 ReAct 循环外抛异常
 * （与 RagMiddleware 的降级哲学一致）。
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
public class KnowledgeRetrievalTool {

    static final String EMPTY_QUERY_MESSAGE = "Please provide a non-empty search query.";
    static final String NO_RESULT_MESSAGE = "No relevant documents found in the knowledge base.";

    private final KnowledgeRetriever retriever;
    private final List<String> kbIds;

    @Tool(
            name = "retrieve_knowledge",
            description = "Retrieve relevant documents from the knowledge base. Use this tool when "
                    + "you need specific information or the user asks about stored knowledge.")
    public String retrieveKnowledge(
            @ToolParam(name = "query", description = "The search query to find relevant documents") String query,
            @ToolParam(name = "limit", description = "Max documents to return (default: 5)", required = false)
                    Integer limit) {
        if (StringUtils.isBlank(query)) {
            return EMPTY_QUERY_MESSAGE;
        }
        List<RetrievedChunk> chunks;
        try {
            chunks = retriever.retrieve(kbIds, query, limit).block();
        } catch (Exception e) {
            // 检索故障不阻断 ReAct 循环，返回友好文案
            log.warn("知识库工具检索失败(kbIds={}): {}", kbIds, e.getMessage());
            return "Knowledge retrieval failed: " + e.getMessage();
        }
        if (chunks == null || chunks.isEmpty()) {
            return NO_RESULT_MESSAGE;
        }
        return formatChunks(chunks);
    }

    // 沿用旧 KnowledgeRetrievalTools 的格式：Retrieved N ... Document i (Score: x.xxx): 内容
    private static String formatChunks(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder("Retrieved ").append(chunks.size()).append(" relevant document(s):\n\n");
        int index = 1;
        for (RetrievedChunk chunk : chunks) {
            sb.append("Document ")
                    .append(index++)
                    .append(" (Score: ")
                    .append(String.format("%.3f", chunk.score()))
                    .append("):\n")
                    .append(chunk.content())
                    .append("\n\n");
        }
        return sb.toString();
    }
}
