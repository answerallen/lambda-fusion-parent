package com.lambda.fusion.ai.llm.model;

import com.lambda.fusion.ai.AiConstants.ModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;

@Data
@Schema(description = "更新 LLM 模型")
public class UpdateLlmModel {

    @Schema(description = "所属提供方ID")
    private String providerId;

    @Schema(description = "模型显示名称")
    private String name;

    @Schema(description = "模型实际名称")
    private String modelName;

    @Schema(description = "模型类型: 1=CHAT, 2=EMBEDDING")
    private ModelType modelType;

    @Schema(description = "默认温度")
    private BigDecimal defaultTemperature;

    @Schema(description = "默认最大 token 数")
    private Integer defaultMaxTokens;

    @Schema(description = "是否支持视觉(图片输入)")
    private Boolean supportsVision;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
