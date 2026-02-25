package com.lambda.fusion.dict.support;

import lombok.Getter;

/**
 * 字典用途枚举
 */
@Getter
public enum DictUsage {

    /**
     * 系统字典
     */
    SYSTEM(0),

    /**
     * 用户字典
     */
    USER(1);

    /**
     * 字典用途值
     */
    private final int value;

    DictUsage(int value) {
        this.value = value;
    }
}
