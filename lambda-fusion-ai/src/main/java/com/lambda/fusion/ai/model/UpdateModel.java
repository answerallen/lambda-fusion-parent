package com.lambda.fusion.ai.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = LlmModelEntity.class)
@Data
@Schema(description = "更新LLM模型DTO")
public class UpdateModel extends BaseDTO<LlmModelEntity> {
    private String name;

    private String displayName;

    private String modelType;

    private String provider;

    private String baseUrl;

    private String apiKeyEncrypted;

    private String modelName;

    private BigDecimal defaultTemperature;
    private Integer defaultMaxTokens;

    @Schema(description = "输入Token单价")
    private BigDecimal inputTokenPrice;

    @Schema(description = "输出Token单价")
    private BigDecimal outputTokenPrice;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否默认模型")
    private Boolean isDefault;
}
