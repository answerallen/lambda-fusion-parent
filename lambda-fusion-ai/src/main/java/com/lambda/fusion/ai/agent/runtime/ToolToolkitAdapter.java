package com.lambda.fusion.ai.agent.runtime;

import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * AgentScope 工具适配器：扫描 Spring 容器中带 {@link Tool} 注解方法的 Bean，{@link #buildToolkit()}
 * 构造 {@link Toolkit} 并注册。MCP 工具经 {@link McpClientAdapter} 产出后注入。
 *
 * @author Jin
 */
@Slf4j
@Component
public class ToolToolkitAdapter implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    // 发现的携带 @Tool 方法的 Bean（启动时扫描，buildToolkit 时注册）
    private final List<Object> toolBeans = new ArrayList<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        scanLocalTools();
    }

    /**
     * 构造 {@link Toolkit} 并注册本地 @Tool。{@code toolIds} 为空/null 时注册全部；非空时仅注册
     * 含匹配 name 的 @Tool 的 bean（按 {@link Tool#name()}，空则用方法名）。单 bean 失败不影响其余。
     */
    public Toolkit buildToolkit(List<String> toolIds) {
        Toolkit toolkit = new Toolkit();
        boolean filter = toolIds != null && !toolIds.isEmpty();
        for (Object bean : toolBeans) {
            if (filter && !hasMatchingTool(bean, toolIds)) {
                continue;
            }
            try {
                toolkit.registerTool(bean);
            } catch (Exception e) {
                log.warn(
                        "ToolToolkitAdapter: 注册 @Tool bean 失败: {}",
                        bean.getClass().getSimpleName(),
                        e);
            }
        }
        return toolkit;
    }

    /** bean 的任一 @Tool name 在 toolIds 中则 true（name 取 {@link Tool#name()}，空用方法名）。 */
    private boolean hasMatchingTool(Object bean, List<String> toolIds) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        for (Method method : targetClass.getDeclaredMethods()) {
            Tool t = method.getAnnotation(Tool.class);
            if (t != null) {
                String name = t.name().isEmpty() ? method.getName() : t.name();
                if (toolIds.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 列出所有本地 @Tool 的 schema（供管理面查询；MCP 工具为 per-agent 装配，不在此列）。
     */
    public List<ToolSchema> getToolSchemas() {
        return buildToolkit(null).getToolSchemas();
    }

    /** 刷新本地 @Tool 扫描（工具 bean 增删后调用）。 */
    public synchronized void refresh() {
        scanLocalTools();
    }

    private void scanLocalTools() {
        toolBeans.clear();
        if (applicationContext == null) {
            return;
        }
        log.info("ToolToolkitAdapter: 开始扫描 Spring 容器中的 AgentScope @Tool 方法...");
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            boolean hasTool = false;
            for (Method method : targetClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    hasTool = true;
                    break;
                }
            }
            if (hasTool) {
                toolBeans.add(bean);
                log.info("ToolToolkitAdapter: 发现 @Tool bean: {} ({})", beanName, targetClass.getSimpleName());
            }
        }
        log.info("ToolToolkitAdapter: 共发现 {} 个 AgentScope @Tool bean", toolBeans.size());
    }
}
