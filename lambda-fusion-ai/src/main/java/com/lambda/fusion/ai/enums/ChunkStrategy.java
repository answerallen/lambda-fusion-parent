package com.lambda.fusion.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文本分段策略枚举
 *
 * @author Jin
 */
@Getter
@AllArgsConstructor
public enum ChunkStrategy {

    /**
     * 固定长度分段
     */
    FIXED("固定长度"),

    /**
     * 按段落分段
     */
    PARAGRAPH("按段落"),

    /**
     * 按句子分段
     */
    SENTENCE("按句子"),

    /**
     * 滑动窗口分段
     */
    SLIDING_WINDOW("滑动窗口");

    private final String description;
}
