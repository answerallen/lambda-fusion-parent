package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发送消息DTO")
public class SendMessage {
    @NotBlank
    private String content;
}
