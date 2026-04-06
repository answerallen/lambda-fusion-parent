package com.lambda.fusion.ai.commons.agent.evaluator;

import com.lambda.fusion.ai.commons.agent.AgentState;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于工具执行结果的边流转条件判定器。
 * 适用于工具调用场景，根据工具执行的成功/失败状态决定流转路径。
 * <p>
 * 表达式格式：
 * - toolSuccess                 (工具执行成功)
 * - toolFailed                  (工具执行失败)
 * - hasResult                   (有返回结果)
 * - result.status == "success"  (结果状态匹配)
 * - result.code == 200          (结果代码匹配)
 * - result.contains("error")    (结果包含特定内容)
 */
@Slf4j
@Component
public class ToolResultConditionEvaluator implements ConditionEvaluator {

    private static final String TOOL_RESULTS_KEY = "toolResults";
    private static final String LAST_TOOL_RESULT_KEY = "lastToolResult";

    @Override
    public boolean evaluate(String expression, AgentState state) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        try {
            ToolResultMetrics metrics = extractMetrics(state);
            return evaluateExpression(expression.trim().toLowerCase(), metrics);

        } catch (Exception e) {
            log.error("工具结果表达式执行异常: {}", expression, e);
            return false;
        }
    }

    /**
     * 从 state 中提取工具执行指标
     */
    @SuppressWarnings("unchecked")
    private ToolResultMetrics extractMetrics(AgentState state) {
        ToolResultMetrics metrics = new ToolResultMetrics();

        if (state.getAttributes() != null) {
            // 获取待执行的工具请求
            if (state.getPendingToolRequests() != null) {
                metrics.pendingToolCount = state.getPendingToolRequests().size();
            }

            // 获取工具执行结果
            Object toolResults = state.getAttributes().get(TOOL_RESULTS_KEY);
            if (toolResults instanceof Map<?, ?> results) {
                metrics.hasResults = !results.isEmpty();
                metrics.resultCount = results.size();
            }

            // 获取最后一个工具结果（独立于 toolResults）
            Object lastResult = state.getAttributes().get(LAST_TOOL_RESULT_KEY);
            if (lastResult instanceof Map<?, ?> resultMap) {
                metrics.lastResult = (Map<String, Object>) resultMap;
                metrics.lastResultSuccess = isSuccessResult(resultMap);
                metrics.hasResults = true;
            }

            // 如果没有 lastToolResult，尝试从 toolResults 中获取最后一个
            if (metrics.lastResult == null && toolResults instanceof Map<?, ?> results && !results.isEmpty()) {
                Object lastKey = null;
                for (Object key : results.keySet()) {
                    lastKey = key;
                }
                if (lastKey != null) {
                    Object result = results.get(lastKey);
                    if (result instanceof Map<?, ?> resultMap) {
                        metrics.lastResult = (Map<String, Object>) resultMap;
                        metrics.lastResultSuccess = isSuccessResult(resultMap);
                    }
                }
            }
        }

        return metrics;
    }

    /**
     * 判断结果是否表示成功
     */
    private boolean isSuccessResult(Map<?, ?> result) {
        // 检查常见的成功标识
        Object success = result.get("success");
        if (success instanceof Boolean bool) {
            return bool;
        }

        Object status = result.get("status");
        if (status instanceof String str) {
            return "success".equalsIgnoreCase(str) || "ok".equalsIgnoreCase(str);
        }

        Object code = result.get("code");
        if (code instanceof Number num) {
            return num.intValue() == 200 || num.intValue() == 0;
        }
        if (code instanceof String str) {
            return "200".equals(str) || "0".equals(str) || "success".equalsIgnoreCase(str);
        }

        // 如果没有错误信息，视为成功
        Object error = result.get("error");
        return error == null;
    }

    /**
     * 解析并执行表达式
     */
    private boolean evaluateExpression(String expr, ToolResultMetrics metrics) {
        String normalizedExpr = expr.replaceAll("\\s+", "");

        // 布尔表达式
        switch (normalizedExpr) {
            case "toolsuccess", "success" -> {
                return metrics.lastResultSuccess;
            }
            case "toolfailed", "failed" -> {
                return !metrics.lastResultSuccess;
            }
            case "hasresult", "hasresults" -> {
                return metrics.hasResults;
            }
            case "haspendingtools" -> {
                return metrics.pendingToolCount > 0;
            }
        }

        // 结果数量表达式
        if (expr.startsWith("resultcount") || expr.startsWith("result.count")) {
            String operator = expr.substring(expr.indexOf("count") + 5).replaceAll("\\s+", "");
            return evaluateCountExpression(operator, metrics.resultCount);
        }

        // 通用 contains 表达式（优先于属性表达式，因为 contains 可能以 result. 开头）
        if (expr.contains("contains")) {
            return evaluateContainsExpression(expr, metrics.lastResult);
        }

        // 结果属性表达式
        if (expr.startsWith("result.")) {
            return evaluateResultPropertyExpression(expr.substring(7), metrics.lastResult);
        }

        log.warn("不支持的工具结果表达式格式: {}", expr);
        return false;
    }

    /**
     * 解析数量表达式
     */
    private boolean evaluateCountExpression(String operator, int count) {
        try {
            if (operator.startsWith(">=")) {
                return count >= Integer.parseInt(operator.substring(2));
            } else if (operator.startsWith(">")) {
                return count > Integer.parseInt(operator.substring(1));
            } else if (operator.startsWith("<=")) {
                return count <= Integer.parseInt(operator.substring(2));
            } else if (operator.startsWith("<")) {
                return count < Integer.parseInt(operator.substring(1));
            } else if (operator.startsWith("==") || operator.startsWith("=")) {
                return count == Integer.parseInt(operator.replaceAll("[^0-9]", ""));
            }
        } catch (NumberFormatException e) {
            log.warn("无法解析数量表达式: {}", operator);
        }
        return false;
    }

    /**
     * 解析结果属性表达式
     */
    private boolean evaluateResultPropertyExpression(String expr, Map<String, Object> result) {
        if (result == null) {
            return false;
        }

        // 提取属性名和比较操作
        String propertyName;
        String operator;
        String value;

        if (expr.contains("==")) {
            int idx = expr.indexOf("==");
            propertyName = expr.substring(0, idx).trim();
            operator = "==";
            value = expr.substring(idx + 2).trim();
        } else if (expr.contains("=")) {
            int idx = expr.indexOf("=");
            propertyName = expr.substring(0, idx).trim();
            operator = "=";
            value = expr.substring(idx + 1).trim();
        } else {
            // 只是属性存在性检查
            propertyName = expr.trim();
            return result.containsKey(propertyName);
        }

        Object actualValue = result.get(propertyName);
        if (actualValue == null) {
            return false;
        }

        // 移除引号
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }

        return actualValue.toString().equalsIgnoreCase(value);
    }

    /**
     * 解析 contains 表达式
     */
    private boolean evaluateContainsExpression(String expr, Map<String, Object> result) {
        if (result == null) {
            return false;
        }

        // 提取 contains 的内容
        int containsIdx = expr.indexOf("contains");
        String beforeContains = expr.substring(0, containsIdx).trim();
        String afterContains = expr.substring(containsIdx + 8).trim();

        // 获取要搜索的内容，移除括号
        String searchContent = afterContains.replaceAll("[()]", "");
        if ((searchContent.startsWith("\"") && searchContent.endsWith("\""))
                || (searchContent.startsWith("'") && searchContent.endsWith("'"))) {
            searchContent = searchContent.substring(1, searchContent.length() - 1);
        }

        // 确定搜索范围
        String searchTarget;
        if (beforeContains.isEmpty() || "result".equals(beforeContains) || "result.".equals(beforeContains)) {
            // 搜索整个结果
            searchTarget = result.toString().toLowerCase();
        } else if (beforeContains.startsWith("result.")) {
            // 搜索特定属性，移除末尾的点号
            String propertyName = beforeContains.substring(7).replaceAll("\\.$", "");
            Object value = result.get(propertyName);
            searchTarget = value != null ? value.toString().toLowerCase() : "";
        } else {
            return false;
        }

        return searchTarget.contains(searchContent.toLowerCase());
    }

    @Override
    public String getType() {
        return "tool";
    }

    /**
     * 工具结果指标数据类
     */
    private static class ToolResultMetrics {
        int pendingToolCount = 0;
        int resultCount = 0;
        boolean hasResults = false;
        boolean lastResultSuccess = false;
        Map<String, Object> lastResult;
    }
}
