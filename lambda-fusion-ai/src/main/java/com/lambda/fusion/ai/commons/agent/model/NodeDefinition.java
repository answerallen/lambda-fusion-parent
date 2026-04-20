package com.lambda.fusion.ai.commons.agent.model;

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

    /**
     * 节点展示名称，供前端画布回显。
     */
    private String label;

    /**
     * 节点图标标识，供前端画布回显。
     */
    private String icon;

    /**
     * 节点主题色，供前端画布回显。
     */
    private String color;

    /**
     * 节点在画布上的坐标。
     */
    private Position position;

    /**
     * 节点在画布上的尺寸。
     */
    private Size size;

    @Data
    public static class Position {
        private Double x;
        private Double y;
    }

    @Data
    public static class Size {
        private Double width;
        private Double height;
    }
}
