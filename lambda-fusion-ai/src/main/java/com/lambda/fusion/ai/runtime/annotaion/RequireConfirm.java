package com.lambda.fusion.ai.runtime.annotaion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 {@code @Tool} 工具需要 HITL（人工确认）：agent 调用本工具前由 agentscope
 * {@code PermissionEngine} 命中 ask 规则，发出 {@code RequireUserConfirmEvent} 暂停，等待用户
 * 确认后继续（第二次 streamEvents 携带 {@code Msg.METADATA_CONFIRM_RESULTS} 恢复）。
 *
 * <p>由 {@code ToolkitAssembler} 启动时扫描所有 {@code @Tool} Bean 动态收集，{@code AgentFactory}
 * 据此构建 {@code PermissionContextState.askRules}（BYPASS 模式 + ask 规则）。工具是否需要确认是
 * 工具的固有属性，跟随工具类声明，而非下游应用配置--避免权限配置分散到 App/DTO/DB。
 *
 * <p>支持方法级与类级标注：方法级仅该工具需确认；类级标注表示该类所有 {@code @Tool} 方法均需确认
 * （方法级优先，类级作为默认）。
 *
 * <p>仅作用于本地 {@code @Tool} 工具；MCP 工具的确认由 MCP 服务端或后续机制支持。
 *
 * <p>示例：
 * <pre>{@code
 * @Tool(name = "query_date", description = "...")
 * @RequireConfirm
 * public String queryDate(...) { ... }
 * }</pre>
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
