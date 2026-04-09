package com.lambda.fusion.ai.commons.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 统一成本计算服务
 * 负责根据token消耗和模型单价计算成本
 *
 * @author Jin
 */
@Slf4j
@Service
public class CostCalculator {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * 成本计算结果
     */
    @Data
    @Builder
    public static class CostResult {
        private BigDecimal inputCost;
        private BigDecimal outputCost;
        private BigDecimal totalCost;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }

    /**
     * 模型统计更新数据
     */
    @Data
    @Builder
    public static class ModelStatistics {
        private String modelId;
        private long callCount;
        private long tokenCount;
        private BigDecimal cost;
    }

    /**
     * 计算单次调用的成本
     *
     * @param promptTokens     输入token数
     * @param completionTokens 输出token数
     * @param inputTokenPrice  输入token单价
     * @param outputTokenPrice 输出token单价
     * @return 成本计算结果
     */
    public CostResult calculateCost(
            int promptTokens, int completionTokens, BigDecimal inputTokenPrice, BigDecimal outputTokenPrice) {

        BigDecimal inputCost = BigDecimal.ZERO;
        BigDecimal outputCost = BigDecimal.ZERO;

        if (inputTokenPrice != null && promptTokens > 0) {
            inputCost =
                    inputTokenPrice.multiply(BigDecimal.valueOf(promptTokens)).setScale(SCALE, ROUNDING_MODE);
        }

        if (outputTokenPrice != null && completionTokens > 0) {
            outputCost = outputTokenPrice
                    .multiply(BigDecimal.valueOf(completionTokens))
                    .setScale(SCALE, ROUNDING_MODE);
        }

        BigDecimal totalCost = inputCost.add(outputCost);

        return CostResult.builder()
                .inputCost(inputCost)
                .outputCost(outputCost)
                .totalCost(totalCost)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .build();
    }

    /**
     * 计算成本（简化版，使用默认单价）
     *
     * @param promptTokens     输入token数
     * @param completionTokens 输出token数
     * @return 成本计算结果
     */
    public CostResult calculateCost(int promptTokens, int completionTokens) {
        return calculateCost(promptTokens, completionTokens, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 验证token统计的一致性
     *
     * @param promptTokens     输入token数
     * @param completionTokens 输出token数
     * @param totalTokens      总token数
     * @return 是否一致
     */
    public boolean validateTokenConsistency(int promptTokens, int completionTokens, int totalTokens) {
        if (promptTokens < 0 || completionTokens < 0 || totalTokens < 0) {
            log.warn(
                    "Token统计异常: 存在负值 promptTokens={}, completionTokens={}, totalTokens={}",
                    promptTokens,
                    completionTokens,
                    totalTokens);
            return false;
        }

        int expectedTotal = promptTokens + completionTokens;
        if (totalTokens != expectedTotal) {
            log.warn(
                    "Token统计不一致: promptTokens({}) + completionTokens({}) = {}, 但totalTokens={}",
                    promptTokens,
                    completionTokens,
                    expectedTotal,
                    totalTokens);
            return false;
        }

        return true;
    }
}
