package com.lambda.fusion.ai.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建会话DTO")
public class CreateSessionDTO {
    private String title;
    private Long kbId;
    private Long userId;
    private Long tenantId;
    private Long llmModelId;
    private String systemPrompt;
}
