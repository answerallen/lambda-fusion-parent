package com.lambda.fusion.ai.agent.factory;

import com.lambda.fusion.ai.agent.AgentGraph;
import com.lambda.fusion.ai.agent.node.LlmProcessingNode;
import com.lambda.fusion.ai.agent.node.ToolExecutingNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 统一托管 RAG 场景下固定图结构的生命周期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGraphProvider {

    private final LlmProcessingNode llmProcessingNode;
    private final ToolExecutingNode toolExecutingNode;

    private volatile AgentGraph ragAgentGraph;

    public AgentGraph getGraph() {
        AgentGraph graph = ragAgentGraph;
        if (graph == null) {
            synchronized (this) {
                graph = ragAgentGraph;
                if (graph == null) {
                    log.info("初始化 RAG AgentGraph 实例");
                    graph = new AgentGraph()
                            .addNode(LlmProcessingNode.NAME, llmProcessingNode)
                            .addNode(ToolExecutingNode.NAME, toolExecutingNode)
                            .addEdge(LlmProcessingNode.NAME, ToolExecutingNode.NAME, null, null)
                            .addEdge(ToolExecutingNode.NAME, LlmProcessingNode.NAME, null, null)
                            .setEntryPoint(LlmProcessingNode.NAME);
                    ragAgentGraph = graph;
                }
            }
        }
        return graph;
    }
}
