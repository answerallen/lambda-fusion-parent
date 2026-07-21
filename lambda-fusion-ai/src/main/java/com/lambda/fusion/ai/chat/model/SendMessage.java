package com.lambda.fusion.ai.chat.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发送消息")
public class SendMessage {

    @Schema(description = "消息内容")
    @NotBlank(message = "消息内容不能为空")
    private String content;
}
