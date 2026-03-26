package com.lambda.fusion.ai.agent.evaluator;

import com.lambda.fusion.ai.agent.AgentState;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Expression Language (SpEL) 的边流转策略判断器。
 * 允许前端用简洁内联属性配置判断图节点，比如: '#state.finished'
 */
@Component
public class SpelConditionEvaluator implements ConditionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public boolean evaluate(String expression, AgentState state) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        StandardEvaluationContext context = new StandardEvaluationContext();
        // 允许直接用 #state.abc 访问状态属性
        context.setVariable("state", state);

        // 提取 attributes 平铺作为快速局部变量供比对 (e.g., #intent == 'search')
        if (state.getAttributes() != null) {
            state.getAttributes().forEach(context::setVariable);
            // 兼容 pendingToolRequests 长度判断
            if (state.getPendingToolRequests() != null) {
                context.setVariable("hasTools", !state.getPendingToolRequests().isEmpty());
            } else {
                context.setVariable("hasTools", false);
            }
        }

        Boolean result = parser.parseExpression(expression).getValue(context, Boolean.class);
        return result != null && result;
    }

    @Override
    public String getType() {
        return "spel";
    }
}
