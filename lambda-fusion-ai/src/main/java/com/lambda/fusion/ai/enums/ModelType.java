package com.lambda.fusion.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 模型类型枚举
 *
 * @author Jin
 */
@Getter
@AllArgsConstructor
public enum ModelType {

    /**
     * 聊天模型
     */
    CHAT("聊天模型"),

    /**
     * Embedding模型(向量化)
     */
    EMBEDDING("Embedding模型"),

    /**
     * 文本补全模型
     */
    COMPLETION("补全模型");

    private final String description;
}
