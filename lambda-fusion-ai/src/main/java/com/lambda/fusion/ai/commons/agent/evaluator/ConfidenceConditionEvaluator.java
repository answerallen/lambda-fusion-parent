package com.lambda.fusion.ai.commons.agent.evaluator;

import com.lambda.fusion.ai.commons.agent.AgentState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于置信度阈值的边流转条件判定器。
 * 适用于 AI 意图识别、分类等场景，根据置信度分数决定流转路径。
 * <p>
 * 表达式格式：
 * - confidence > 0.8          (大于阈值)
 * - confidence >= 0.9         (大于等于阈值)
 * - confidence < 0.5          (小于阈值)
 * - 0.3 <= confidence <= 0.7  (区间范围)
 */
@Slf4j
@Component
public class ConfidenceConditionEvaluator implements ConditionEvaluator {

    private static final String CONFIDENCE_KEY = "confidence";
    private static final String INTENT_KEY = "intent";

    @Override
    public boolean evaluate(String expression, AgentState state) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        try {
            Double confidence = extractConfidence(state);
            if (confidence == null) {
                log.warn("无法从 state 中提取置信度值，表达式: {}", expression);
                return false;
            }

            return evaluateExpression(expression.trim(), confidence);

        } catch (Exception e) {
            log.error("置信度表达式执行异常: {}", expression, e);
            return false;
        }
    }

    /**
     * 从 state 中提取置信度值
     */
    private Double extractConfidence(AgentState state) {
        // 1. 优先从 attributes 中获取 confidence
        if (state.getAttributes() != null) {
            Object confidenceValue = state.getAttributes().get(CONFIDENCE_KEY);
            if (confidenceValue instanceof Number number) {
                return number.doubleValue();
            }

            // 2. 尝试从 intent 对象中获取 confidence
            Object intent = state.getAttributes().get(INTENT_KEY);
            if (intent instanceof java.util.Map<?, ?> intentMap) {
                Object intentConfidence = intentMap.get(CONFIDENCE_KEY);
                if (intentConfidence instanceof Number number) {
                    return number.doubleValue();
                }
            }
        }

        // 3. 尝试从最后一条 AI 消息中提取
        if (state.getMessages() != null && !state.getMessages().isEmpty()) {
            var lastMessage = state.getMessages().get(state.getMessages().size() - 1);
            if (lastMessage instanceof dev.langchain4j.data.message.AiMessage aiMessage) {
                // 这里可以根据实际需求扩展，从 metadata 中提取置信度
            }
        }

        return null;
    }

    /**
     * 解析并执行表达式
     */
    private boolean evaluateExpression(String expression, double confidence) {
        // 去除空格
        String expr = expression.replaceAll("\\s+", "").toLowerCase();

        // 处理区间表达式: 0.3<=confidence<=0.7
        if (expr.contains("<=") && expr.split("<=").length == 3) {
            return evaluateRangeExpression(expr, confidence);
        }

        // 处理单边表达式
        if (expr.startsWith(CONFIDENCE_KEY)) {
            return evaluateSingleExpression(expr.substring(CONFIDENCE_KEY.length()), confidence);
        }

        // 处理反向表达式: 0.8>confidence
        if (expr.endsWith(CONFIDENCE_KEY)) {
            String operator = expr.substring(0, expr.length() - CONFIDENCE_KEY.length());
            return evaluateReverseExpression(operator, confidence);
        }

        log.warn("不支持的置信度表达式格式: {}", expression);
        return false;
    }

    /**
     * 解析单边表达式: >0.8, >=0.9, <0.5, <=0.6, ==0.7
     */
    private boolean evaluateSingleExpression(String operator, double confidence) {
        try {
            double threshold = extractThreshold(operator);

            if (operator.startsWith(">=")) {
                return confidence >= threshold;
            } else if (operator.startsWith(">")) {
                return confidence > threshold;
            } else if (operator.startsWith("<=")) {
                return confidence <= threshold;
            } else if (operator.startsWith("<")) {
                return confidence < threshold;
            } else if (operator.startsWith("==") || operator.startsWith("=")) {
                return Math.abs(confidence - threshold) < 0.0001;
            }
        } catch (NumberFormatException e) {
            log.warn("无法解析阈值: {}", operator);
        }
        return false;
    }

    /**
     * 解析反向表达式: 0.8>, 0.9>=, 0.5<, 0.6<=
     * 例如 "0.8>confidence" 实际是 confidence < 0.8
     */
    private boolean evaluateReverseExpression(String expr, double confidence) {
        String trimmed = expr.trim();

        try {
            if (trimmed.endsWith(">=")) {
                double threshold = Double.parseDouble(
                        trimmed.substring(0, trimmed.length() - 2).trim());
                return confidence <= threshold;
            } else if (trimmed.endsWith(">")) {
                double threshold = Double.parseDouble(
                        trimmed.substring(0, trimmed.length() - 1).trim());
                return confidence < threshold;
            } else if (trimmed.endsWith("<=")) {
                double threshold = Double.parseDouble(
                        trimmed.substring(0, trimmed.length() - 2).trim());
                return confidence >= threshold;
            } else if (trimmed.endsWith("<")) {
                double threshold = Double.parseDouble(
                        trimmed.substring(0, trimmed.length() - 1).trim());
                return confidence > threshold;
            } else {
                double threshold = Double.parseDouble(trimmed);
                return threshold > confidence;
            }
        } catch (NumberFormatException e) {
            log.warn("无法解析反向表达式阈值: {}", expr);
            return false;
        }
    }

    /**
     * 解析区间表达式: min<=confidence<=max
     */
    private boolean evaluateRangeExpression(String expr, double confidence) {
        try {
            String[] parts = expr.split("<=");
            if (parts.length == 3 && CONFIDENCE_KEY.equals(parts[1])) {
                double min = Double.parseDouble(parts[0]);
                double max = Double.parseDouble(parts[2]);
                return confidence >= min && confidence <= max;
            }
        } catch (NumberFormatException e) {
            log.warn("无法解析区间表达式: {}", expr);
        }
        return false;
    }

    /**
     * 从操作符字符串中提取阈值
     */
    private double extractThreshold(String operator) {
        String numberStr = operator.replaceAll("[^0-9.]", "");
        return Double.parseDouble(numberStr);
    }

    @Override
    public String getType() {
        return "confidence";
    }
}
