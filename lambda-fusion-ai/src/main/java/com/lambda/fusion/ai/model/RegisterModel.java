package com.lambda.fusion.ai.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = LlmModelEntity.class)
@Data
@Schema(description = "注册LLM模型DTO")
public class RegisterModel extends BaseDTO<LlmModelEntity> {
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

    @Schema(description = "输入Token单价")
    private BigDecimal inputTokenPrice;

    @Schema(description = "输出Token单价")
    private BigDecimal outputTokenPrice;
}
