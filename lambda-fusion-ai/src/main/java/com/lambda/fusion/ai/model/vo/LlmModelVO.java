package com.lambda.fusion.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "LLM模型VO")
public class LlmModelVO {
    private Long id;
    private String modelId;
    private String name;
    private String displayName;
    private String modelType;
    private String provider;
    private String baseUrl;
    private String apiKeyEncrypted;
    private String modelName;
    private BigDecimal defaultTemperature;
    private Integer defaultMaxTokens;
    private Boolean enabled;
    private Boolean isDefault;
    private Long totalCalls;
    private Long totalTokens;
    private BigDecimal totalCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
