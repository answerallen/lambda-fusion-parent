package com.lambda.fusion.ai.support.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档处理状态枚举
 *
 * @author Jin
 */
@Getter
@AllArgsConstructor
public enum DocumentStatus {

    /**
     * 待处理
     */
    PENDING("待处理"),

    /**
     * 处理中
     */
    PROCESSING("处理中"),

    /**
     * 已完成
     */
    COMPLETED("已完成"),

    /**
     * 处理失败
     */
    FAILED("失败");

    private final String description;
}
