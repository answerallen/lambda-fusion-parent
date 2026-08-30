package com.lambda.fusion.ai.runtime.annotaion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注本地业务工具类（{@code @Tool} Bean）：被 {@code ToolkitAssembler} 容器启动时扫描，
 * 统一注册为共享 Agent 工具。类级可声明 {@code requireConfirm}，作用该类所有 {@code @Tool} 方法
 * （方法级单独确认用 {@link RequireConfirm}）。
 *
 * @author Jin
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiTool {

    /**
     * 类级整批 HITL 人工确认开关：为 true 时该类所有 {@code @Tool} 方法调用前都需人工确认。
     *
     * @return 默认 false（不自动确认）
     */
    boolean requireConfirm() default false;
}