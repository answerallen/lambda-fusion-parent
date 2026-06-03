package com.lambda.fusion.ai.agent.model;

import java.util.List;
import lombok.Data;

/**
 * 对应前端工作流可视化画布导出的 JSON 图谱结构
 */
@Data
public class GraphDefinition {
    /**
     * 图的入口节点名称/ID
     */
    private String entryPoint;

    /**
     * 全局声明的节点集
     */
    private List<NodeDefinition> nodes;

    /**
     * 全局声明的连线与边条件关系集
     */
    private List<EdgeDefinition> edges;
}
