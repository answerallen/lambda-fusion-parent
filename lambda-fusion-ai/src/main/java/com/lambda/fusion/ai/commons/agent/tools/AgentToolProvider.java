package com.lambda.fusion.ai.commons.agent.tools;

import com.lambda.fusion.ai.commons.support.mcp.McpClientManager;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 工具提供者
 * <p>
 * 统一管理两类工具来源：
 * <ol>
 *   <li>本地 {@code @Tool} 注解方法（扫描 Spring 容器中所有携带 @Tool 注解的 Bean）</li>
 *   <li>远程 MCP（Model Context Protocol）Server 工具（通过 {@link McpClientManager} 动态加载）</li>
 * </ol>
 * 两类工具对外提供统一的 {@link #getToolSpecifications()} 和 {@link #executeTool(ToolExecutionRequest)} 接口，
 * {@link com.lambda.fusion.ai.commons.agent.node.LlmProcessingNode} 等节点无感知地使用全部工具。
 * <p>
 * 工具执行优先级：本地 @Tool > MCP 工具（同名冲突时本地优先）
 */
@Slf4j
@Configuration
public class AgentToolProvider implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    private final ObjectMapper objectMapper;

    // ===== 本地 @Tool 缓存 =====
    private final List<ToolSpecification> localToolSpecifications = new ArrayList<>();
    private final Map<String, ToolExecutorMethod> localMethodMap = new HashMap<>();

    // ===== MCP 工具缓存（volatile 保证可见性，synchronized 方法保证原子性）=====
    private volatile List<ToolSpecification> mcpToolSpecifications = new ArrayList<>();
    private volatile Map<String, McpToolExecutor> mcpMethodMap = new HashMap<>();

    private McpClientManager mcpClientManager;

    public AgentToolProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Lazy
    @Autowired(required = false)
    public void setMcpClientManager(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    // ========================== ApplicationContextAware ==========================

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        scanLocalTools();
        // 应用启动后异步加载 MCP 工具（Spring 上下文就绪后才能获取 McpClientManager）
        loadMcpTools();
    }

    // ========================== 公共 API ==========================

    /**
     * 获取所有可用工具规格（本地 @Tool + MCP 工具的合集）
     */
    public List<ToolSpecification> getToolSpecifications() {
        List<ToolSpecification> all = new ArrayList<>(localToolSpecifications);
        all.addAll(mcpToolSpecifications);
        return Collections.unmodifiableList(all);
    }

    /**
     * 获取指定名称的工具规格子集（适用于节点配置了 allowedTools 的场景）
     */
    public List<ToolSpecification> getToolSpecifications(Set<String> includedToolNames) {
        if (includedToolNames == null || includedToolNames.isEmpty()) {
            return getToolSpecifications();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String toolName : includedToolNames) {
            if (toolName != null && !toolName.isBlank()) {
                normalized.add(toolName.trim());
            }
        }
        if (normalized.isEmpty()) {
            return getToolSpecifications();
        }
        return getToolSpecifications().stream()
                .filter(spec -> normalized.contains(spec.name()))
                .toList();
    }

    /**
     * 判断指定工具名是否已注册（本地或 MCP 均算）
     */
    public boolean hasTool(String toolName) {
        if (toolName == null) return false;
        return localMethodMap.containsKey(toolName) || mcpMethodMap.containsKey(toolName);
    }

    /**
     * 执行工具请求
     * <p>
     * 执行优先级：本地 @Tool 方法 > MCP 远程工具
     *
     * @param request 工具执行请求
     * @return 工具执行结果字符串
     */
    public String executeTool(ToolExecutionRequest request) {
        // 1. 优先查本地 @Tool
        ToolExecutorMethod localExecutor = localMethodMap.get(request.name());
        if (localExecutor != null) {
            return executeLocalTool(request, localExecutor);
        }
        // 2. 查 MCP 远程工具
        McpToolExecutor mcpExecutor = mcpMethodMap.get(request.name());
        if (mcpExecutor != null) {
            return mcpExecutor.execute(request);
        }
        log.warn("AgentToolProvider: 未找到工具 '{}'，将返回错误信息", request.name());
        return "Error: Tool '" + request.name() + "' not found.";
    }

    /**
     * 刷新 MCP 工具列表
     * <p>
     * 在 MCP Server 配置发生增删改时调用，重新从数据库加载所有启用的 MCP 服务器，
     * 并获取其工具规格列表。
     */
    public synchronized void refreshMcpTools() {
        loadMcpTools();
    }

    // ========================== 本地 @Tool 扫描 ==========================

    private void scanLocalTools() {
        log.info("AgentToolProvider: 开始扫描 Spring 容器中的 @Tool 方法...");
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }

            // 获取非 AOP 代理类的 Class
            Class<?> targetClass = org.springframework.aop.support.AopUtils.getTargetClass(bean);

            // 使用 LangChain4j 的规范提取该类下的所有 @Tool 规格
            List<ToolSpecification> specs;
            try {
                specs = ToolSpecifications.toolSpecificationsFrom(targetClass);
            } catch (Exception e) {
                continue;
            }

            if (specs != null && !specs.isEmpty()) {
                localToolSpecifications.addAll(specs);

                // 缓存实际的执行 Method（用于 executeTool）
                for (Method method : targetClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Tool.class)) {
                        Tool toolAnnotation = method.getAnnotation(Tool.class);
                        String name = toolAnnotation.name();
                        if (name == null || name.isEmpty()) {
                            name = method.getName();
                        }
                        if (localMethodMap.containsKey(name)) {
                            log.warn(
                                    "AgentToolProvider: 检测到同名本地工具冲突: '{}', 来自 bean '{}', 方法 '{}'。将覆盖之前的注册。",
                                    name,
                                    beanName,
                                    method.getName());
                        }
                        localMethodMap.put(name, new ToolExecutorMethod(bean, method));
                        log.info("AgentToolProvider: 注册本地 @Tool: {}", name);
                    }
                }
            }
        }
        log.info("AgentToolProvider: 本地 @Tool 扫描完成，共注册 {} 个工具", localToolSpecifications.size());
    }

    private String executeLocalTool(ToolExecutionRequest request, ToolExecutorMethod executor) {
        try {
            Map<String, Object> arguments = new HashMap<>();
            if (request.arguments() != null && !request.arguments().isEmpty()) {
                arguments = objectMapper.readValue(request.arguments(), new TypeReference<>() {
                    @Override
                    public Type getType() {
                        return super.getType();
                    }
                });
            }

            Method method = executor.method();
            Object[] args = new Object[method.getParameterCount()];
            Parameter[] parameters = method.getParameters();

            for (int i = 0; i < parameters.length; i++) {
                String paramName = parameters[i].getName();
                Object argValue = arguments.get(paramName);
                if (argValue != null) {
                    args[i] = objectMapper.convertValue(argValue, parameters[i].getType());
                }
            }

            Object result = method.invoke(executor.bean(), args);
            return result != null ? result.toString() : "Success";

        } catch (Exception e) {
            log.error("AgentToolProvider: 执行本地 @Tool '{}' 异常", request.name(), e);
            return "Error during tool execution: " + e.getMessage();
        }
    }

    // ========================== MCP 工具加载 ==========================

    /**
     * 从 McpClientManager 加载所有启用的 MCP 工具
     */
    private void loadMcpTools() {
        if (mcpClientManager == null) {
            log.debug("AgentToolProvider: McpClientManager 未注入（可能未引入 MCP 依赖或无 MCP 配置），跳过 MCP 工具加载");
            return;
        }
        try {
            List<McpClient> clients = mcpClientManager.getAllEnabledClients();
            if (clients.isEmpty()) {
                log.info("AgentToolProvider: 无可用的 MCP 服务器，MCP 工具列表为空");
                mcpToolSpecifications = new ArrayList<>();
                mcpMethodMap = new HashMap<>();
                return;
            }

            List<ToolSpecification> newMcpSpecs = new ArrayList<>();
            Map<String, McpToolExecutor> newMcpMap = new HashMap<>();

            for (McpClient client : clients) {
                for (ToolSpecification spec : client.listTools()) {
                    if (localMethodMap.containsKey(spec.name())) {
                        log.warn("AgentToolProvider: MCP 工具 '{}' 与本地 @Tool 同名，已跳过（本地优先）", spec.name());
                        continue;
                    }
                    if (newMcpMap.containsKey(spec.name())) {
                        log.warn(
                                "AgentToolProvider: MCP 工具 '{}' 在多个服务器间重名（当前服务器 key={}），已跳过后续重复项",
                                spec.name(),
                                client.key());
                        continue;
                    }
                    newMcpSpecs.add(spec);
                    newMcpMap.put(spec.name(), new McpToolExecutor(client, spec.name()));
                    log.info("AgentToolProvider: 注册 MCP 工具: {} (server={})", spec.name(), client.key());
                }
            }

            // 原子更新（避免读取到中间状态）
            this.mcpToolSpecifications = new ArrayList<>(newMcpSpecs);
            this.mcpMethodMap = newMcpMap;

            log.info("AgentToolProvider: MCP 工具加载完成，共注册 {} 个工具", newMcpMap.size());
        } catch (Exception e) {
            log.error("AgentToolProvider: MCP 工具加载失败（已降级为仅使用本地工具）", e);
            // 加载失败时保持当前状态（不清空已有工具），防止服务降级
        }
    }

    // ========================== 内部记录类 ==========================

    private record ToolExecutorMethod(Object bean, Method method) {}

    /**
     * MCP 工具执行器（直接封装对 McpClient 的调用）
     */
    private record McpToolExecutor(McpClient client, String toolName) {

        public String execute(ToolExecutionRequest request) {
            try {
                ToolExecutionResult result = client.executeTool(request);
                if (result == null) {
                    return "Success";
                }
                String resultText = result.resultText();
                if (result.isError()) {
                    return "Error during MCP tool execution: " + (resultText != null ? resultText : "Unknown error");
                }
                return resultText != null ? resultText : String.valueOf(result.result());
            } catch (Exception e) {
                log.error("AgentToolProvider: 执行 MCP 工具 '{}' 异常", toolName, e);
                return "Error during MCP tool execution: " + e.getMessage();
            }
        }
    }
}
