package com.lambda.fusion.ai.commons.agent;

/**
 * Agent工作流图的执行节点抽象接口
 */
public interface AgentNode {

    /**
     * 节点名称唯一标识
     */
    String getName();

    ExecutionResult execute(AgentState state);

    record ExecutionResult(AgentState state, String nextNode) {}
}
