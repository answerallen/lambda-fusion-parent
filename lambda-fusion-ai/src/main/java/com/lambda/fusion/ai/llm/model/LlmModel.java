package com.lambda.fusion.ai.llm.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "LLM模型VO")
public class LlmModel {
    private String id;
    private String name;
    private String displayName;
    private String modelType;
    private String provider;
    private String baseUrl;
    private String apiKeyEncrypted;
    private String apiVersion;
    private String deploymentName;
    private String modelName;
    private BigDecimal defaultTemperature;
    private Integer defaultMaxTokens;
    private BigDecimal defaultTopP;
    private Integer contextWindow;
    private String rateLimitConfig;
    private BigDecimal inputTokenPrice;
    private BigDecimal outputTokenPrice;
    private String capabilities;
    private Boolean enabled;
    private Boolean isDefault;
    private Long totalCalls;
    private Long totalTokens;
    private BigDecimal totalCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
