package com.lambda.fusion.ai.chat.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建对话会话")
public class CreateSession {

    @Schema(description = "应用ID")
    @NotBlank(message = "应用ID不能为空")
    private String appId;

    @Schema(description = "会话标题")
    private String title;
}
