package com.lambda.fusion.ai.llm.model;

import com.lambda.fusion.ai.AiConstants.ModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
@Schema(description = "创建 LLM 模型")
public class CreateLlmModel {

    @Schema(description = "所属提供方ID")
    @NotBlank(message = "提供方ID不能为空")
    private String providerId;

    @Schema(description = "模型显示名称")
    @NotBlank(message = "模型名称不能为空")
    private String name;

    @Schema(description = "模型实际名称")
    @NotBlank(message = "模型实际名称不能为空")
    private String modelName;

    @Schema(description = "模型类型: 1=CHAT, 2=EMBEDDING")
    @NotNull(message = "模型类型不能为空")
    private ModelType modelType;

    @Schema(description = "默认温度")
    private BigDecimal defaultTemperature;

    @Schema(description = "默认最大 token 数")
    private Integer defaultMaxTokens;

    @Schema(description = "是否启用")
    private Boolean enabled = Boolean.TRUE;
}
