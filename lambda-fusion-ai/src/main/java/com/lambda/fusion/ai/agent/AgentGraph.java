package com.lambda.fusion.ai.agent;

import com.lambda.fusion.ai.agent.evaluator.ConditionEvaluator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 智能体可视化工作流执行引擎
 *
 * 线程安全设计：
 * - edges 列表使用 Collections.synchronizedList 保证线程安全
 * - nodes 映射使用 Collections.synchronizedMap 保证线程安全
 */
@Slf4j
public class AgentGraph {

    public static final String END_NODE = "END";

    private final Map<String, AgentNode> nodes = Collections.synchronizedMap(new HashMap<>());
    private final List<Edge> edges = Collections.synchronizedList(new ArrayList<>());
    private String startNodeId;

    @Data
    public static class Edge {
        private String sourceId;
        private String targetId;
        private ConditionEvaluator conditionEvaluator;
        private String conditionExpression;
    }

    /**
     * 注册节点(带独立ID前缀)
     */
    public AgentGraph addNode(String id, AgentNode node) {
        if (!StringUtils.hasText(id) || node == null) {
            throw new IllegalArgumentException("Invalid node registration");
        }
        nodes.put(id, node);
        return this;
    }

    /**
     * 增加边缘连线规划
     */
    public AgentGraph addEdge(String sourceId, String targetId, ConditionEvaluator evaluator, String expression) {
        Edge edge = new Edge();
        edge.setSourceId(sourceId);
        edge.setTargetId(targetId);
        edge.setConditionEvaluator(evaluator);
        edge.setConditionExpression(expression);
        edges.add(edge);
        return this;
    }

    /**
     * 声明图启动的入口Node ID
     */
    public AgentGraph setEntryPoint(String nodeId) {
        if (!nodes.containsKey(nodeId)) {
            throw new IllegalArgumentException("Node id " + nodeId + " must be added before setting as entry point");
        }
        this.startNodeId = nodeId;
        return this;
    }

    /**
     * 引擎启动，按照 Edges 与 Node 返回态进行流转分发
     */
    public AgentState invoke(AgentState state) {
        if (startNodeId == null) {
            throw new IllegalStateException("Entry point not set");
        }

        String currentNodeId = startNodeId;
        long loopCount = 0;
        final long MAX_LOOPS = 25; // 防止错误连边导致的无限死循环递归

        while (!AgentGraph.END_NODE.equals(currentNodeId) && !state.isFinished()) {
            AgentNode node = nodes.get(currentNodeId);
            if (node == null) {
                log.error("在Graph上下文中找不到试图进入的 Node ID: '{}'", currentNodeId);
                break;
            }

            loopCount++;
            if (loopCount > MAX_LOOPS) {
                log.error("AgentGraph 超过最大递归深度截断: {}", MAX_LOOPS);
                break;
            }

            log.debug("AgentGraph -> Executing Node ID: {} (Type: {})", currentNodeId, node.getName());

            // 1. 获取节点自身的建议流转路由
            String suggestedNext = node.execute(state);

            // 2. 边缘路由解析与覆盖
            // 如果节点硬性返回了下一个节点的名字/ID，优先跳往；如果没有（即可视化下解耦的情况），则评估画布 Edges
            if (StringUtils.hasText(suggestedNext)) {
                currentNodeId = suggestedNext;
            } else {
                currentNodeId = evaluateEdgesForNext(currentNodeId, state);
                if (currentNodeId == null) {
                    log.warn("当前节点 '{}' 执行完毕后没有匹配到任何有效的出向边，图执行被迫停止。", node.getName());
                    break;
                }
            }
        }

        log.debug("AgentGraph -> Execution Finish!");
        return state;
    }

    private String evaluateEdgesForNext(String currentNodeId, AgentState state) {
        for (Edge edge : edges) {
            if (edge.getSourceId().equals(currentNodeId)) {
                // 判断条件
                if (edge.getConditionEvaluator() != null && StringUtils.hasText(edge.getConditionExpression())) {
                    boolean isPass = edge.getConditionEvaluator().evaluate(edge.getConditionExpression(), state);
                    if (isPass) {
                        return edge.getTargetId();
                    }
                } else {
                    // 没有配置验证器，视为直通车
                    return edge.getTargetId();
                }
            }
        }
        return null;
    }
}
