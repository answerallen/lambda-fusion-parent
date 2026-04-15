package com.lambda.fusion.ai.commons.agent.factory;

import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.evaluator.ConditionEvaluator;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.commons.agent.model.EdgeDefinition;
import com.lambda.fusion.ai.commons.agent.model.GraphDefinition;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 将前端传来的 JSON Schema (GraphDefinition)
 * 转换为真实存在的内存执行引擎 (AgentGraph) 的对象装配工厂
 * <p>
 * <strong>节点共享设计</strong>：
 * 同一类型的多个节点实例将共享同一个 Spring Bean。
 * 例如，图中两个 {@code LLM_PROCESSOR} 类型的节点将绑定同一个 {@code LlmProcessingNode} Bean。
 * <p>
 * <strong>无状态约束</strong>：
 * 由于节点共享，所有 {@link AgentNode} 实现必须保持无状态。
 * 节点属性通过 {@link com.lambda.fusion.ai.commons.agent.AgentState#getCurrentNodeProperties()} 获取，
 * 而不是存储在节点实例中。
 * <p>
 * <strong>线程安全</strong>：
 * 返回的 {@link AgentGraph} 实例是线程安全的，可被多个工作流执行并发使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGraphFactory {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    /**
     * 根据基于图可视化描述JSON构建工作流底座
     */
    public AgentGraph buildFromDefinition(String jsonDef) throws Exception {
        return buildFromDefinition(jsonDef, null);
    }

    /**
     * 根据基于图可视化描述JSON构建工作流底座，并应用图构建参数
     */
    public AgentGraph buildFromDefinition(String jsonDef, AgentGraphBuildOptions options) throws Exception {
        GraphDefinition def = objectMapper.readValue(jsonDef, GraphDefinition.class);
        return buildFromDefinition(def, options);
    }

    public AgentGraph buildFromDefinition(GraphDefinition def) {
        return buildFromDefinition(def, null);
    }

    public AgentGraph buildFromDefinition(GraphDefinition def, AgentGraphBuildOptions options) {
        AgentGraph graph = new AgentGraph();

        // 装载当前系统提供的所有边缘策略解析器
        Map<String, ConditionEvaluator> evaluators =
                applicationContext.getBeansOfType(ConditionEvaluator.class).values().stream()
                        .collect(Collectors.toMap(ConditionEvaluator::getType, e -> e));

        // 装载各个 Nodes 并按照其唯一 ID 编发
        // 注意：同类型的节点共享同一个 Spring Bean，属性通过 AgentState 传递
        if (def.getNodes() != null) {
            for (var nodeDef : def.getNodes()) {
                AgentNode matchedNode = null;
                // 按 Type 匹配对应的 Bean 实例
                for (AgentNode bean :
                        applicationContext.getBeansOfType(AgentNode.class).values()) {
                    if (bean.getName().equals(nodeDef.getType())) {
                        matchedNode = bean;
                        break;
                    }
                }

                if (matchedNode != null) {
                    // 节点 ID 不同，但节点实例可能相同（同类型共享 Bean）
                    // 属性存储在 AgentGraph.nodeProperties 中，执行时注入 AgentState
                    graph.addNode(nodeDef.getId(), matchedNode, nodeDef.getProperties());
                } else {
                    log.warn("无法挂载节点 ID:{}, 未找到类型为 {} 的原生 AgentNode 扩展。", nodeDef.getId(), nodeDef.getType());
                }
            }
        }

        // 挂载边缘条件关系网络
        if (def.getEdges() != null) {
            for (EdgeDefinition edge : def.getEdges()) {
                ConditionEvaluator evaluator = null;
                if (edge.getConditionType() != null && !edge.getConditionType().isEmpty()) {
                    evaluator = evaluators.get(edge.getConditionType());
                    if (evaluator == null) {
                        throw new AiBusinessException(
                                AiErrorCode.WORKFLOW_CONFIG_INVALID,
                                "未找到条件评估器类型: "
                                        + edge.getConditionType()
                                        + ", edge="
                                        + edge.getSource()
                                        + "->"
                                        + edge.getTarget());
                    }
                }
                graph.addEdge(edge.getSource(), edge.getTarget(), evaluator, edge.getConditionExpression());
            }
        }

        graph.setEntryPoint(def.getEntryPoint());
        applyBuildOptions(graph, options);
        return graph;
    }

    private void applyBuildOptions(AgentGraph graph, AgentGraphBuildOptions options) {
        if (graph == null || options == null) {
            return;
        }
        CompileConfig compileConfig = options.getCompileConfig();
        if (compileConfig != null) {
            graph.setCompileConfig(compileConfig);
        }
        Integer maxIterations = options.getMaxIterations();
        if (maxIterations != null) {
            graph.setMaxIterations(maxIterations);
        }
    }
}
