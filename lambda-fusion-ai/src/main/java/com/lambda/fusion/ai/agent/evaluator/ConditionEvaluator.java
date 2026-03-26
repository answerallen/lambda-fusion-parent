package com.lambda.fusion.ai.agent.evaluator;

import com.lambda.fusion.ai.agent.AgentState;

/**
 * 边流转条件判定器核心接口
 */
public interface ConditionEvaluator {

    /**
     * 解析边关联的表达式，决定是否满足通行要求
     *
     * @param expression 具体的规则表达式 (类似 JS 片段或者 SpEL)
     * @param state 运行时上下文状态，提供断言所需的数据支撑
     * @return 满足则流转此条连线，否则评估下一条线
     */
    boolean evaluate(String expression, AgentState state);

    /**
     * 判定器支持的具体类型标识 (如 "spel", "js")
     */
    String getType();
}
