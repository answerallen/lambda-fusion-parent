package com.lambda.fusion.ai.chat.suooprt;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * 成本计算结果
 */
@Data
@Builder
public class CostResult {
    private BigDecimal inputCost;
    private BigDecimal outputCost;
    private BigDecimal totalCost;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
}
