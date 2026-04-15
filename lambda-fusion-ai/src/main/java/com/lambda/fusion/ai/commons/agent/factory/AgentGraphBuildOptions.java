package com.lambda.fusion.ai.commons.agent.factory;

import lombok.Data;
import org.bsc.langgraph4j.CompileConfig;

/**
 * AgentGraph 的可选构建参数。
 */
@Data
public class AgentGraphBuildOptions {

    /**
     * LangGraph4j 编译参数。
     */
    private CompileConfig compileConfig;

    /**
     * 图执行的最大递归/迭代次数。
     */
    private Integer maxIterations;
}
