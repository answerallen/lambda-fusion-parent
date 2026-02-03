package com.lambda.fusion.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "注册LLM模型DTO")
public class RegisterModelDTO {
    @NotBlank
    private String name;
    private String displayName;
    @NotBlank
    private String modelType;
    @NotBlank
    private String provider;
    @NotBlank
    private String baseUrl;
    @NotBlank
    private String apiKeyEncrypted;
    @NotBlank
    private String modelName;
    private BigDecimal defaultTemperature;
    private Integer defaultMaxTokens;
}
