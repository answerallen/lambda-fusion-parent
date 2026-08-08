package com.lambda.fusion.ai.runtime.annotaion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 {@code @Tool} 工具需要 HITL 人工确认：调用前触发 {@code RequireUserConfirmEvent} 暂停，
 * 用户确认后恢复。由 {@code ToolkitAssembler} 启动时扫描，{@code AgentFactory} 构建 ASK 规则。
 *
 * <p>支持方法级（单工具）与类级（该类所有 @Tool 方法）标注。仅本地 @Tool 工具，MCP 后续。
 *
 * @author Jin
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireConfirm {

    /**
     * 确认提示语，供前端确认 UI 展示（可选）。
     *
     * @return 提示语，空则由前端按工具名生成默认文案
     */
    String value() default "";
}
