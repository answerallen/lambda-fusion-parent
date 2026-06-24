package com.lambda.fusion.ai.chat.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "发送消息DTO")
public class SendMessage {

    @NotBlank(message = "消息内容不能为空")
    @Size(min = 1, max = 10000, message = "消息长度必须在1-10000字符之间")
    @Schema(description = "消息内容", example = "你好，请帮我解答这个问题")
    private String content;
}
