package com.lambda.fusion.ai.commons.agent.evaluator;

import com.lambda.fusion.ai.commons.agent.AgentState;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于 RAG 检索相关性的边流转条件判定器。
 * 适用于知识库问答场景，根据检索结果的相关性分数决定流转路径。
 * <p>
 * 表达式格式：
 * - relevance > 0.7           (平均相关性大于阈值)
 * - maxRelevance >= 0.8       (最高相关性大于等于阈值)
 * - minRelevance > 0.5        (最低相关性大于阈值)
 * - hasRelevantDocs           (是否存在相关文档)
 * - docCount >= 3             (检索到的文档数量)
 */
@Slf4j
@Component
public class RetrievalRelevanceConditionEvaluator implements ConditionEvaluator {

    private static final String RETRIEVAL_RESULTS_KEY = "retrievalResults";
    private static final String RELEVANCE_KEY = "relevance";
    private static final String SCORE_KEY = "score";

    @Override
    public boolean evaluate(String expression, AgentState state) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        try {
            RetrievalMetrics metrics = extractMetrics(state);
            return evaluateExpression(expression.trim().toLowerCase(), metrics);

        } catch (Exception e) {
            log.error("检索相关性表达式执行异常: {}", expression, e);
            return false;
        }
    }

    /**
     * 从 state 中提取检索指标
     */
    private RetrievalMetrics extractMetrics(AgentState state) {
        RetrievalMetrics metrics = new RetrievalMetrics();

        if (state.getAttributes() != null) {
            Object results = state.getAttributes().get(RETRIEVAL_RESULTS_KEY);

            if (results instanceof List<?> resultList) {
                metrics.docCount = resultList.size();

                if (resultList.isEmpty()) {
                    return metrics;
                }

                double sumRelevance = 0;
                double maxRelevance = 0;
                double minRelevance = 1;

                for (Object item : resultList) {
                    if (item instanceof Map<?, ?> doc) {
                        Double relevance = extractRelevanceScore(doc);
                        if (relevance != null) {
                            sumRelevance += relevance;
                            maxRelevance = Math.max(maxRelevance, relevance);
                            minRelevance = Math.min(minRelevance, relevance);
                        }
                    }
                }

                metrics.avgRelevance = sumRelevance / metrics.docCount;
                metrics.maxRelevance = maxRelevance;
                metrics.minRelevance = minRelevance == 1 ? 0 : minRelevance;

                // 判断是否有相关文档（平均相关性 > 0.5 视为有相关）
                metrics.hasRelevantDocs = metrics.avgRelevance > 0.5;
            }
        }

        return metrics;
    }

    /**
     * 从文档对象中提取相关性分数
     */
    private Double extractRelevanceScore(Map<?, ?> doc) {
        // 尝试不同的字段名
        Object score = doc.get(RELEVANCE_KEY);
        if (score == null) {
            score = doc.get(SCORE_KEY);
        }
        if (score == null) {
            score = doc.get("similarity");
        }
        if (score == null) {
            score = doc.get("distance");
            // 距离越小相关性越高，需要转换
            if (score instanceof Number distance) {
                return 1.0 - Math.min(distance.doubleValue(), 1.0);
            }
        }

        if (score instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    /**
     * 解析并执行表达式
     */
    private boolean evaluateExpression(String expr, RetrievalMetrics metrics) {
        // 处理布尔表达式
        if ("hasrelevantdocs".equals(expr.replaceAll("\\s+", ""))) {
            return metrics.hasRelevantDocs;
        }

        // 处理数量表达式: docCount >= 3
        if (expr.startsWith("doccount")) {
            return evaluateCountExpression(expr.substring("doccount".length()), metrics.docCount);
        }

        // 处理相关性表达式
        if (expr.startsWith("relevance")) {
            return evaluateRelevanceExpression(expr.substring("relevance".length()), metrics.avgRelevance);
        }
        if (expr.startsWith("avg relevance") || expr.startsWith("avgrelevance")) {
            String remaining = expr.startsWith("avg relevance")
                    ? expr.substring("avg relevance".length())
                    : expr.substring("avgrelevance".length());
            return evaluateRelevanceExpression(remaining, metrics.avgRelevance);
        }
        if (expr.startsWith("max relevance") || expr.startsWith("maxrelevance")) {
            String remaining = expr.startsWith("max relevance")
                    ? expr.substring("max relevance".length())
                    : expr.substring("maxrelevance".length());
            return evaluateRelevanceExpression(remaining, metrics.maxRelevance);
        }
        if (expr.startsWith("min relevance") || expr.startsWith("minrelevance")) {
            String remaining = expr.startsWith("min relevance")
                    ? expr.substring("min relevance".length())
                    : expr.substring("minrelevance".length());
            return evaluateRelevanceExpression(remaining, metrics.minRelevance);
        }

        log.warn("不支持的检索相关性表达式格式: {}", expr);
        return false;
    }

    /**
     * 解析数量表达式
     */
    private boolean evaluateCountExpression(String operator, int count) {
        operator = operator.replaceAll("\\s+", "");

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
     * 解析相关性表达式
     */
    private boolean evaluateRelevanceExpression(String operator, double relevance) {
        operator = operator.replaceAll("\\s+", "");

        try {
            double threshold = extractThreshold(operator);

            if (operator.startsWith(">=")) {
                return relevance >= threshold;
            } else if (operator.startsWith(">")) {
                return relevance > threshold;
            } else if (operator.startsWith("<=")) {
                return relevance <= threshold;
            } else if (operator.startsWith("<")) {
                return relevance < threshold;
            } else if (operator.startsWith("==") || operator.startsWith("=")) {
                return Math.abs(relevance - threshold) < 0.0001;
            }
        } catch (NumberFormatException e) {
            log.warn("无法解析相关性表达式: {}", operator);
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
        return "retrieval";
    }

    /**
     * 检索指标数据类
     */
    private static class RetrievalMetrics {
        int docCount = 0;
        double avgRelevance = 0;
        double maxRelevance = 0;
        double minRelevance = 0;
        boolean hasRelevantDocs = false;
    }
}
