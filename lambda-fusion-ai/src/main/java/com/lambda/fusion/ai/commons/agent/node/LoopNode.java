package com.lambda.fusion.ai.commons.agent.node;

import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.evaluator.ConditionEvaluator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 循环节点。
 * 支持 while/do-while/for 三种循环模式。
 * <p>
 * 配置属性：
 * - loopType: 循环类型 (while | doWhile | for)
 * - condition: 循环条件表达式
 * - conditionType: 条件评估器类型 (spel | js | confidence 等)
 * - maxIterations: 最大循环次数（防止死循环，默认 100）
 * - loopBody: 循环体节点ID
 * - exitNode: 退出循环后跳转的节点ID
 * - forInit: for 循环初始化表达式（仅 for 类型）
 * - forUpdate: for 循环更新表达式（仅 for 类型）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoopNode implements AgentNode {

    private static final String LOOP_CONTEXT_KEY = "__loop_context__";
    private static final int DEFAULT_MAX_ITERATIONS = 100;

    private final Map<String, ConditionEvaluator> evaluators;

    @Override
    public String getName() {
        return "LOOP";
    }

    @Override
    public ExecutionResult execute(AgentState state) {
        Map<String, Object> properties = state.getCurrentNodeProperties();

        if (properties == null) {
            log.warn("循环节点缺少配置属性");
            return new ExecutionResult(state, null);
        }

        String loopType = (String) properties.getOrDefault("loopType", "while");
        String condition = (String) properties.get("condition");
        String conditionType = (String) properties.getOrDefault("conditionType", "spel");
        int maxIterations = (int) properties.getOrDefault("maxIterations", DEFAULT_MAX_ITERATIONS);
        String loopBody = (String) properties.get("loopBody");
        String exitNode = (String) properties.get("exitNode");

        // 获取或初始化循环上下文
        LoopContext context = getOrCreateLoopContext(state);

        // 检查是否超过最大迭代次数
        if (context.iterationCount >= maxIterations) {
            log.warn("循环节点达到最大迭代次数限制: {}", maxIterations);
            clearLoopContext(state);
            return new ExecutionResult(state, exitNode);
        }

        // 根据循环类型处理
        return switch (loopType.toLowerCase()) {
            case "dowhile", "do-while" -> executeDoWhile(state, condition, conditionType, loopBody, exitNode, context);
            case "for" -> executeFor(state, properties, loopBody, exitNode, context);
            default -> executeWhile(state, condition, conditionType, loopBody, exitNode, context);
        };
    }

    /**
     * 执行 while 循环
     */
    private ExecutionResult executeWhile(
            AgentState state,
            String condition,
            String conditionType,
            String loopBody,
            String exitNode,
            LoopContext context) {
        // 首次进入或继续循环时检查条件
        if (context.iterationCount > 0 || !context.conditionChecked) {
            ConditionEvaluator evaluator = evaluators.get(conditionType);
            if (evaluator == null) {
                log.warn("未找到条件评估器类型: {}", conditionType);
                clearLoopContext(state);
                return new ExecutionResult(state, exitNode);
            }

            boolean shouldContinue = evaluator.evaluate(condition, state);
            context.conditionChecked = true;

            if (!shouldContinue) {
                // 条件不满足，退出循环
                clearLoopContext(state);
                log.debug("while 循环条件不满足，退出循环");
                return new ExecutionResult(state, exitNode);
            }
        }

        // 条件满足，继续循环
        context.iterationCount++;
        context.conditionChecked = false;
        saveLoopContext(state, context);

        log.debug("while 循环第 {} 次迭代", context.iterationCount);
        return new ExecutionResult(state, loopBody);
    }

    /**
     * 执行 do-while 循环
     */
    private ExecutionResult executeDoWhile(
            AgentState state,
            String condition,
            String conditionType,
            String loopBody,
            String exitNode,
            LoopContext context) {
        // do-while 先执行循环体，再检查条件
        if (!context.bodyExecuted) {
            // 首次执行循环体
            context.bodyExecuted = true;
            saveLoopContext(state, context);
            log.debug("do-while 循环执行循环体");
            return new ExecutionResult(state, loopBody);
        }

        // 循环体执行完毕，检查条件
        ConditionEvaluator evaluator = evaluators.get(conditionType);
        if (evaluator == null) {
            log.warn("未找到条件评估器类型: {}", conditionType);
            clearLoopContext(state);
            return new ExecutionResult(state, exitNode);
        }

        boolean shouldContinue = evaluator.evaluate(condition, state);

        if (shouldContinue) {
            // 继续循环
            context.iterationCount++;
            context.bodyExecuted = false;
            saveLoopContext(state, context);
            log.debug("do-while 循环继续第 {} 次迭代", context.iterationCount);
            return new ExecutionResult(state, loopBody);
        } else {
            // 退出循环
            clearLoopContext(state);
            log.debug("do-while 循环条件不满足，退出循环");
            return new ExecutionResult(state, exitNode);
        }
    }

    /**
     * 执行 for 循环
     */
    private ExecutionResult executeFor(
            AgentState state, Map<String, Object> properties, String loopBody, String exitNode, LoopContext context) {
        // for 循环简化实现，复用 while 逻辑
        // 实际项目中可以实现完整的 for 循环语义
        String condition = (String) properties.get("condition");
        String conditionType = (String) properties.getOrDefault("conditionType", "spel");

        return executeWhile(state, condition, conditionType, loopBody, exitNode, context);
    }

    /**
     * 获取或创建循环上下文
     */
    private LoopContext getOrCreateLoopContext(AgentState state) {
        if (state.getAttributes() != null) {
            Object contextObj = state.getAttributes().get(LOOP_CONTEXT_KEY);
            if (contextObj instanceof Map<?, ?> contextMap) {
                LoopContext context = new LoopContext();
                context.iterationCount = (int) contextMap.get("iterationCount");
                context.conditionChecked = (boolean) contextMap.get("conditionChecked");
                context.bodyExecuted = (boolean) contextMap.get("bodyExecuted");
                return context;
            }
        }
        return new LoopContext();
    }

    /**
     * 保存循环上下文
     */
    private void saveLoopContext(AgentState state, LoopContext context) {
        if (state.getAttributes() != null) {
            Map<String, Object> contextMap = new java.util.HashMap<>();
            contextMap.put("iterationCount", context.iterationCount);
            contextMap.put("conditionChecked", context.conditionChecked);
            contextMap.put("bodyExecuted", context.bodyExecuted);
            state.getAttributes().put(LOOP_CONTEXT_KEY, contextMap);
        }
    }

    /**
     * 清除循环上下文
     */
    private void clearLoopContext(AgentState state) {
        if (state.getAttributes() != null) {
            state.getAttributes().remove(LOOP_CONTEXT_KEY);
        }
    }

    /**
     * 循环上下文
     */
    private static class LoopContext {
        int iterationCount = 0;
        boolean conditionChecked = false;
        boolean bodyExecuted = false;
    }
}
