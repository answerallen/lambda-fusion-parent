package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建会话DTO")
public class CreateSession {
    private String title;
    private String kbId;
    private String llmModelId;
    private String robotId;
    private String systemPrompt;
}
