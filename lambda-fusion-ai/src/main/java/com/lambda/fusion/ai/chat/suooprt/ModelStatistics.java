package com.lambda.fusion.ai.chat.suooprt;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * 模型统计更新数据
 */
@Data
@Builder
public class ModelStatistics {
    private String modelId;
    private long callCount;
    private long tokenCount;
    private BigDecimal cost;
}
