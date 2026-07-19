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
 * AgentScope 工具适配器（取代旧 {@code AgentToolProvider} 产出 langchain4j {@code ToolSpecification} 的职责）。
 *
 * <p>扫描 Spring 容器中携带 AgentScope {@link Tool} 注解方法的 Bean，构造 AgentScope {@link Toolkit}：
 * <ul>
 *   <li>{@link #buildToolkit()} 创建 {@code new Toolkit()} 并 {@link Toolkit#registerTool(Object)} 注册所有发现的 @Tool bean；</li>
 *   <li>注解是 {@code io.agentscope.core.tool.Tool}（**非** langchain4j 的 {@code dev.langchain4j.agent.tool.Tool}），
 *       S9 迁移=改 import 包名（{@link ToolToolkitAdapter} 自动接管，旧 {@code AgentToolProvider} 在 Phase 3 删除）。</li>
 * </ul>
 *
 * <p>Phase 1：提供本地 @Tool 装配能力；按 app {@code toolIds}/{@code toolGroups} 过滤/激活在 Phase 2
 * （待 {@code AppEntity.toolIds}/{@code toolGroups} 字段落地）。MCP 工具经 {@link McpClientAdapter} 产出
 * {@link io.agentscope.core.tool.mcp.McpClientWrapper} 后 {@link Toolkit#registerMcpClient} 注入。
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
     * 构造一个注册了所有本地 @Tool 的 {@link Toolkit}。
     *
     * <p>每次构造新 Toolkit（为后续按 app 注入 MCP/Tool Group 留空间）；注册过程防御性吞异常，
     * 单个 bean 失败不影响其余。无 @Tool bean 时返回空 Toolkit（agent 无工具可用，不影响对话）。
     */
    public Toolkit buildToolkit() {
        Toolkit toolkit = new Toolkit();
        for (Object bean : toolBeans) {
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

    /**
     * 列出所有本地 @Tool 的 schema（供管理面查询；MCP 工具为 per-agent 装配，不在此列）。
     */
    public List<ToolSchema> getToolSchemas() {
        return buildToolkit().getToolSchemas();
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
