package com.lambda.fusion.ai.agent.model;

import java.util.Map;
import lombok.Data;

@Data
public class NodeDefinition {
    /**
     * 可视化节点的唯一标识
     */
    private String id;

    /**
     * 绑定的组件类型标识 (例如 LLM_PROCESSOR, TOOL_EXECUTOR 等)
     */
    private String type;

    /**
     * 各个组件自带的具体配置属性，可在初始化或执行期下发解析
     */
    private Map<String, Object> properties;
}
