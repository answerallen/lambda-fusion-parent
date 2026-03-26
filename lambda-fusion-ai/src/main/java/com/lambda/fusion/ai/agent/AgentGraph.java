package com.lambda.fusion.ai.agent;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 智能体工作流执行引擎
 * 类似最小化的 LangGraph 运行时
 */
@Slf4j
public class AgentGraph {

    public static final String END_NODE = "END";

    private final Map<String, AgentNode> nodes = new HashMap<>();
    private String startNodeName;

    /**
     * 注册节点
     */
    public AgentGraph addNode(AgentNode node) {
        if (node == null || !StringUtils.hasText(node.getName())) {
            throw new IllegalArgumentException("Invalid node");
        }
        nodes.put(node.getName(), node);
        return this;
    }

    /**
     * 声明启动节点
     */
    public AgentGraph setEntryPoint(String nodeName) {
        if (!nodes.containsKey(nodeName)) {
            throw new IllegalArgumentException("Node must be added before setting as entry point");
        }
        this.startNodeName = nodeName;
        return this;
    }

    /**
     * 使用给定的初始状态启动并遍历图
     */
    public AgentState invoke(AgentState state) {
        if (startNodeName == null) {
            throw new IllegalStateException("Entry point not set");
        }

        String currentNodeName = startNodeName;
        long loopCount = 0;
        final long MAX_LOOPS = 20; // 避免死循环跑飞

        while (!AgentGraph.END_NODE.equals(currentNodeName) && !state.isFinished()) {
            AgentNode node = nodes.get(currentNodeName);
            if (node == null) {
                throw new IllegalStateException("Next node '" + currentNodeName + "' not found in graph");
            }

            loopCount++;
            if (loopCount > MAX_LOOPS) {
                log.error("AgentGraph 超过最大递归深度截断: {}", MAX_LOOPS);
                break;
            }

            log.debug("AgentGraph -> Executing Node: {}", currentNodeName);

            // 执行节点逻辑并获取下一个状态
            currentNodeName = node.execute(state);

            if (currentNodeName == null) {
                log.warn("Node {} returned null, forcing termination.", node.getName());
                break;
            }
        }

        log.debug("AgentGraph -> Execution Finish!");
        return state;
    }
}
