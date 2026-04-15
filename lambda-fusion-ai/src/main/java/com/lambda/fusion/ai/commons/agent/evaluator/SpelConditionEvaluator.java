package com.lambda.fusion.ai.commons.agent.evaluator;

import com.lambda.fusion.ai.commons.agent.AgentState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Expression Language (SpEL) 的边流转策略判断器。
 * 允许前端用简洁内联属性配置判断图节点，比如: '#state.finished'
 * <p>
 * 安全设计：
 * - 使用 SimpleEvaluationContext 替代 StandardEvaluationContext，限制可访问的对象
 * - 只允许访问 AgentState 的特定属性
 * - 禁止访问系统对象和反射调用
 */
@Slf4j
@Component
public class SpelConditionEvaluator implements ConditionEvaluator {

    private static final String[] EXECUTION_CONTEXT_KEYS = {
        "executionId",
        "userId",
        "tenantId",
        "username",
        "orgId",
        "roles",
        "isAdmin",
        "isDev",
        "isManager",
        "isTenantManager",
        "isAnyManager"
    };

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public boolean evaluate(String expression, AgentState state) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        // 表达式安全验证
        if (!isExpressionSafe(expression)) {
            log.warn("检测到不安全的 SpEL 表达式，已拒绝执行: {}", expression);
            return false;
        }

        try {
            SimpleEvaluationContext context =
                    SimpleEvaluationContext.forReadOnlyDataBinding().build();
            context.setVariable("state", state);
            context.setVariable("attributes", state.getAttributes());
            context.setVariable("attrs", state.getAttributes());
            context.setVariable("currentNodeId", state.getCurrentNodeId());
            context.setVariable("currentNodeType", state.getCurrentNodeType());
            context.setVariable("currentNodeProperties", state.getCurrentNodeProperties());
            context.setVariable("nodeProps", state.getCurrentNodeProperties());
            context.setVariable("graphNodeProperties", state.getGraphNodeProperties());
            context.setVariable("graphProps", state.getGraphNodeProperties());
            context.setVariable("sessionId", state.getSessionId());
            context.setVariable("kbId", state.getKbId());
            context.setVariable("llmModelId", state.getLlmModelId());
            injectExecutionContextVariables(context, state);

            if (state.getAttributes() != null) {
                state.getAttributes().forEach((key, value) -> {
                    try {
                        context.setVariable(key, value);
                    } catch (Exception e) {
                        log.debug("无法设置变量 {}: {}", key, e.getMessage());
                    }
                });

                if (state.getPendingToolRequests() != null) {
                    context.setVariable(
                            "hasTools", !state.getPendingToolRequests().isEmpty());
                } else {
                    context.setVariable("hasTools", false);
                }
            } else {
                context.setVariable(
                        "hasTools",
                        state.getPendingToolRequests() != null
                                && !state.getPendingToolRequests().isEmpty());
            }

            Boolean result = parser.parseExpression(expression).getValue(context, Boolean.class);
            return result != null && result;

        } catch (Exception e) {
            log.error("SpEL 表达式执行异常: {}", expression, e);
            return false;
        }
    }

    private void injectExecutionContextVariables(SimpleEvaluationContext context, AgentState state) {
        if (state.getAttributes() == null) {
            return;
        }
        for (String key : EXECUTION_CONTEXT_KEYS) {
            context.setVariable(key, state.getAttributes().get(key));
        }
    }

    /**
     * 验证表达式是否安全
     * 检查是否包含危险的操作符或方法调用
     */
    private boolean isExpressionSafe(String expression) {
        if (expression == null) {
            return true;
        }

        String expr = expression.toLowerCase();

        // 禁止的危险操作符和方法
        String[] dangerousPatterns = {
            "t(", // 类型转换
            "new ", // 对象创建
            "class", // 类访问
            "getclass", // 获取类
            "forname", // 反射
            "getmethod", // 反射
            "invoke", // 反射调用
            "runtime", // 运行时
            "exec", // 执行命令
            "system", // 系统调用
            "processs", // 进程
            "constructor", // 构造器
            "field", // 字段反射
            "method" // 方法反射
        };

        for (String pattern : dangerousPatterns) {
            if (expr.contains(pattern)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getType() {
        return "spel";
    }
}
