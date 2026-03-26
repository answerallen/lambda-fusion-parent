package com.lambda.fusion.ai.agent;

/**
 * Agent工作流图的执行节点抽象接口
 */
public interface AgentNode {

    /**
     * 节点名称唯一标识
     */
    String getName();

    /**
     * 执行该节点的具体逻辑
     * @param state 贯穿整个状态机的状态持有对象
     * @return 返回下一步需要流转到的 Node 的 Name。若返回 "END" 则图结束。
     */
    String execute(AgentState state);
}
