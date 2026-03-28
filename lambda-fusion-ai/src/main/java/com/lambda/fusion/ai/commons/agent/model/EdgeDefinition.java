package com.lambda.fusion.ai.commons.agent.model;

import lombok.Data;

@Data
public class EdgeDefinition {
    /**
     * 源节点ID
     */
    private String source;

    /**
     * 目标流转节点ID
     */
    private String target;

    /**
     * 条件匹配计算类型 (例如: spel / js)
     * 如果为空，代表这是一条硬连接（无条件直通边）
     */
    private String conditionType;

    /**
     * 控制源是否能路由到目标的脚本表达式本体
     */
    private String conditionExpression;
}
