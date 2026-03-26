package com.lambda.fusion.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

/**
 * Agent动作提供者
 * 扫描Spring容器中包含 @Tool 的方法并提取签名和执行逻辑
 */
@Slf4j
@Configuration
public class AgentToolProvider implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<ToolSpecification> allToolSpecifications = new ArrayList<>();
    private final Map<String, ToolExecutorMethod> methodMap = new HashMap<>();

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        scanTools();
    }

    private void scanTools() {
        log.info("AgentToolProvider: 开始扫描 Spring 容器中的 @Tool 方法...");
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }

            // 获取非AOP代理类的Class
            Class<?> targetClass = org.springframework.aop.support.AopUtils.getTargetClass(bean);

            // 使用 LangChain4j 的规范提取该类下的所有 @Tool
            List<ToolSpecification> specs;
            try {
                specs = ToolSpecifications.toolSpecificationsFrom(targetClass);
            } catch (Exception e) {
                continue; // 当前 bean 没有匹配或有错误
            }

            if (specs != null && !specs.isEmpty()) {
                allToolSpecifications.addAll(specs);

                // 根据生成的名字缓存实际的执行 Method
                Method[] methods = targetClass.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(Tool.class)) {
                        Tool toolAnnotation = method.getAnnotation(Tool.class);
                        String name = toolAnnotation.name();
                        if (name == null || name.isEmpty()) {
                            name = method.getName();
                        }
                        methodMap.put(name, new ToolExecutorMethod(bean, method));
                        log.info("注册 Agent Tool: {}", name);
                    }
                }
            }
        }
    }

    public List<ToolSpecification> getToolSpecifications() {
        return allToolSpecifications;
    }

    /**
     * 实际执行 Tool 请求
     */
    public String executeTool(ToolExecutionRequest request) {
        ToolExecutorMethod executor = methodMap.get(request.name());
        if (executor == null) {
            return "Error: Tool '" + request.name() + "' not found.";
        }

        try {
            Map<String, Object> arguments = new HashMap<>();
            if (request.arguments() != null && !request.arguments().isEmpty()) {
                arguments = objectMapper.readValue(request.arguments(), new TypeReference<>() {});
            }

            Method method = executor.method;
            Object[] args = new Object[method.getParameterCount()];
            Parameter[] parameters = method.getParameters();

            for (int i = 0; i < parameters.length; i++) {
                String paramName = parameters[i].getName();
                Object argValue = arguments.get(paramName);
                if (argValue != null) {
                    // Type conversion
                    args[i] = objectMapper.convertValue(argValue, parameters[i].getType());
                }
            }

            Object result = method.invoke(executor.bean, args);
            return result != null ? result.toString() : "Success";

        } catch (Exception e) {
            log.error("执行Tool {} 异常", request.name(), e);
            return "Error during tool execution: " + e.getMessage();
        }
    }

    private record ToolExecutorMethod(Object bean, Method method) {}
}
