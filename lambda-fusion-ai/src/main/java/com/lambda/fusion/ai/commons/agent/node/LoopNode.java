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

    private static final String LEGACY_LOOP_CONTEXT_KEY = "__loop_context__";
    private static final String LOOP_CONTEXTS_KEY = "__loop_contexts__";
    private static final String DEFAULT_LOOP_CONTEXT_ID = "__default__";
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
        Object maxIterObj = properties.getOrDefault("maxIterations", DEFAULT_MAX_ITERATIONS);
        int maxIterations = maxIterObj instanceof Number n ? n.intValue() : DEFAULT_MAX_ITERATIONS;
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
            context.iterationCount++;
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
            context.bodyExecuted = false;
            saveLoopContext(state, context);
            log.debug("do-while 循环继续第 {} 次迭代", context.iterationCount + 1);
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
        String condition = (String) properties.get("condition");
        String conditionType = (String) properties.getOrDefault("conditionType", "spel");
        String forInit = (String) properties.get("forInit");
        String forUpdate = (String) properties.get("forUpdate");
        Object maxIterObj = properties.getOrDefault("maxIterations", DEFAULT_MAX_ITERATIONS);
        int maxIterations = maxIterObj instanceof Number n ? n.intValue() : DEFAULT_MAX_ITERATIONS;

        ConditionEvaluator evaluator = evaluators.get(conditionType);
        if (evaluator == null) {
            log.warn("未找到条件评估器类型: {}", conditionType);
            clearLoopContext(state);
            return new ExecutionResult(state, exitNode);
        }

        if (!context.initialized && forInit != null && !forInit.isBlank()) {
            executeInitExpression(state, forInit);
            context.initialized = true;
            saveLoopContext(state, context);
        }

        if (context.pendingUpdate && forUpdate != null && !forUpdate.isBlank()) {
            executeUpdateExpression(state, forUpdate);
            context.pendingUpdate = false;
        }

        if (context.iterationCount >= maxIterations) {
            log.warn("for 循环达到最大迭代次数限制: {}", maxIterations);
            clearLoopContext(state);
            return new ExecutionResult(state, exitNode);
        }

        boolean shouldContinue = evaluator.evaluate(condition, state);
        if (!shouldContinue) {
            clearLoopContext(state);
            log.debug("for 循环条件不满足，退出循环");
            return new ExecutionResult(state, exitNode);
        }

        context.iterationCount++;
        context.pendingUpdate = true;
        saveLoopContext(state, context);

        log.debug("for 循环第 {} 次迭代", context.iterationCount);
        return new ExecutionResult(state, loopBody);
    }

    /**
     * 执行 for 循环初始化表达式
     */
    private void executeInitExpression(AgentState state, String forInit) {
        try {
            if (forInit.contains("=")) {
                String[] parts = forInit.split("=", 2);
                if (parts.length == 2) {
                    String varName = parts[0].trim();
                    String valueExpr = parts[1].trim();
                    Object value = parseValueExpression(valueExpr);

                    if (value != null) {
                        if (state.getAttributes() == null) {
                            state.setAttributes(new java.util.HashMap<>());
                        }
                        state.getAttributes().put(varName, value);
                        log.debug("for 循环初始化: {} = {}", varName, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("for 循环初始化表达式执行失败: {}", forInit, e);
        }
    }

    /**
     * 解析值表达式
     */
    private Object parseValueExpression(String valueExpr) {
        if (valueExpr.matches("\\d+")) {
            return Integer.parseInt(valueExpr);
        } else if (valueExpr.matches("\\d+\\.\\d+")) {
            return Double.parseDouble(valueExpr);
        } else if (valueExpr.startsWith("\"") && valueExpr.endsWith("\"")) {
            return valueExpr.substring(1, valueExpr.length() - 1);
        } else if (valueExpr.equals("true")) {
            return true;
        } else if (valueExpr.equals("false")) {
            return false;
        }
        log.warn("无法解析值表达式: {}", valueExpr);
        return null;
    }

    /**
     * 执行 for 循环更新表达式
     */
    private void executeUpdateExpression(AgentState state, String forUpdate) {
        try {
            if (forUpdate.contains("++")) {
                String varName = forUpdate.replace("++", "").trim();
                if (state.getAttributes() != null && state.getAttributes().containsKey(varName)) {
                    Object current = state.getAttributes().get(varName);
                    if (current instanceof Integer) {
                        state.getAttributes().put(varName, (Integer) current + 1);
                        printDebugMessage(varName, (Integer) current + 1);
                    }
                }
            } else if (forUpdate.contains("--")) {
                String varName = forUpdate.replace("--", "").trim();
                if (state.getAttributes() != null && state.getAttributes().containsKey(varName)) {
                    Object current = state.getAttributes().get(varName);
                    if (current instanceof Integer) {
                        state.getAttributes().put(varName, (Integer) current - 1);
                        printDebugMessage(varName, (Integer) current - 1);
                    }
                }
            } else if (forUpdate.contains("+=")) {
                String[] parts = forUpdate.split("\\+=", 2);
                if (parts.length == 2) {
                    String varName = parts[0].trim();
                    String incrementStr = parts[1].trim();
                    if (state.getAttributes() != null && state.getAttributes().containsKey(varName)) {
                        Object current = state.getAttributes().get(varName);
                        int increment = Integer.parseInt(incrementStr);
                        if (current instanceof Integer) {
                            state.getAttributes().put(varName, (Integer) current + increment);
                            printDebugMessage(varName, (Integer) current + increment);
                        }
                    }
                }
            } else if (forUpdate.contains("-=")) {
                String[] parts = forUpdate.split("-=", 2);
                if (parts.length == 2) {
                    String varName = parts[0].trim();
                    String decrementStr = parts[1].trim();
                    if (state.getAttributes() != null && state.getAttributes().containsKey(varName)) {
                        Object current = state.getAttributes().get(varName);
                        int decrement = Integer.parseInt(decrementStr);
                        if (current instanceof Integer) {
                            state.getAttributes().put(varName, (Integer) current - decrement);
                            printDebugMessage(varName, (Integer) current - decrement);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("for 循环更新表达式执行失败: {}", forUpdate, e);
        }
    }

    private static void printDebugMessage(String varName, int current) {
        log.debug("for 循环更新: {} = {}", varName, current);
    }

    /**
     * 获取或创建循环上下文
     */
    private LoopContext getOrCreateLoopContext(AgentState state) {
        String contextId = getLoopContextId(state);
        if (state.getAttributes() != null) {
            Object contextsObj = state.getAttributes().get(LOOP_CONTEXTS_KEY);
            if (contextsObj instanceof Map<?, ?> contextsMap) {
                Object contextObj = contextsMap.get(contextId);
                if (contextObj instanceof Map<?, ?> contextMap) {
                    return parseLoopContext(contextMap);
                }
                // 已启用多上下文容器时，不再回退到旧全局 key，避免跨节点污染。
                return new LoopContext();
            }
            // 向后兼容：仍支持历史版本单一 key 结构。
            Object legacyContextObj = state.getAttributes().get(LEGACY_LOOP_CONTEXT_KEY);
            if (legacyContextObj instanceof Map<?, ?> contextMap) {
                return parseLoopContext(contextMap);
            }
        }
        return new LoopContext();
    }

    /**
     * 保存循环上下文
     */
    private void saveLoopContext(AgentState state, LoopContext context) {
        if (state.getAttributes() == null) {
            state.setAttributes(new java.util.HashMap<>());
        }
        String contextId = getLoopContextId(state);
        Map<String, Object> contextMap = toContextMap(context);
        Map<String, Object> contexts = getOrCreateContextsContainer(state);
        contexts.put(contextId, contextMap);
        // 保留旧字段，兼容外部依赖该键读取循环上下文的场景。
        state.getAttributes().put(LEGACY_LOOP_CONTEXT_KEY, contextMap);
    }

    /**
     * 清除循环上下文
     */
    private void clearLoopContext(AgentState state) {
        if (state.getAttributes() != null) {
            Map<String, Object> contexts = getContextsContainer(state);
            if (contexts != null) {
                contexts.remove(getLoopContextId(state));
                if (contexts.isEmpty()) {
                    state.getAttributes().remove(LOOP_CONTEXTS_KEY);
                }
            }
            state.getAttributes().remove(LEGACY_LOOP_CONTEXT_KEY);
        }
    }

    private String getLoopContextId(AgentState state) {
        String nodeId = state.getCurrentNodeId();
        return nodeId == null || nodeId.isBlank() ? DEFAULT_LOOP_CONTEXT_ID : nodeId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateContextsContainer(AgentState state) {
        Object existing = state.getAttributes().get(LOOP_CONTEXTS_KEY);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> contexts = new java.util.HashMap<>();
        state.getAttributes().put(LOOP_CONTEXTS_KEY, contexts);
        return contexts;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getContextsContainer(AgentState state) {
        Object existing = state.getAttributes().get(LOOP_CONTEXTS_KEY);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private LoopContext parseLoopContext(Map<?, ?> contextMap) {
        LoopContext context = new LoopContext();
        Object iterCount = contextMap.get("iterationCount");
        context.iterationCount = iterCount instanceof Number n ? n.intValue() : 0;
        context.conditionChecked = Boolean.TRUE.equals(contextMap.get("conditionChecked"));
        context.bodyExecuted = Boolean.TRUE.equals(contextMap.get("bodyExecuted"));
        context.pendingUpdate = Boolean.TRUE.equals(contextMap.get("pendingUpdate"));
        context.initialized = Boolean.TRUE.equals(contextMap.get("initialized"));
        return context;
    }

    private Map<String, Object> toContextMap(LoopContext context) {
        Map<String, Object> contextMap = new java.util.HashMap<>();
        contextMap.put("iterationCount", context.iterationCount);
        contextMap.put("conditionChecked", context.conditionChecked);
        contextMap.put("bodyExecuted", context.bodyExecuted);
        contextMap.put("pendingUpdate", context.pendingUpdate);
        contextMap.put("initialized", context.initialized);
        return contextMap;
    }

    /**
     * 循环上下文
     */
    private static class LoopContext {
        int iterationCount = 0;
        boolean conditionChecked = false;
        boolean bodyExecuted = false;
        boolean pendingUpdate = false;
        boolean initialized = false;
    }
}
