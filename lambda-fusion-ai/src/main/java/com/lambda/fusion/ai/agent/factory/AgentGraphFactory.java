package com.lambda.fusion.ai.agent.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.fusion.ai.agent.AgentGraph;
import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.evaluator.ConditionEvaluator;
import com.lambda.fusion.ai.agent.model.EdgeDefinition;
import com.lambda.fusion.ai.agent.model.GraphDefinition;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 将前端传来的 JSON Schema (GraphDefinition)
 * 转换为真实存在的内存执行引擎 (AgentGraph) 的对象装配工厂表
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
        GraphDefinition def = objectMapper.readValue(jsonDef, GraphDefinition.class);
        return buildFromDefinition(def);
    }

    public AgentGraph buildFromDefinition(GraphDefinition def) {
        AgentGraph graph = new AgentGraph();

        // 装载当前系统提供的所有边缘策略解析器
        Map<String, ConditionEvaluator> evaluators =
                applicationContext.getBeansOfType(ConditionEvaluator.class).values().stream()
                        .collect(Collectors.toMap(ConditionEvaluator::getType, e -> e));

        // 装载各个 Nodes 并按照其唯一 ID 编发
        if (def.getNodes() != null) {
            for (var nodeDef : def.getNodes()) {
                AgentNode matchedNode = null;
                // 按 Type 取消对应容器实例，从而实现依赖继承与动态织入
                for (AgentNode bean :
                        applicationContext.getBeansOfType(AgentNode.class).values()) {
                    if (bean.getName().equals(nodeDef.getType())) {
                        matchedNode = bean;
                        break;
                    }
                }

                if (matchedNode != null) {
                    graph.addNode(nodeDef.getId(), matchedNode);
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
                }
                graph.addEdge(edge.getSource(), edge.getTarget(), evaluator, edge.getConditionExpression());
            }
        }

        graph.setEntryPoint(def.getEntryPoint());
        return graph;
    }
}
