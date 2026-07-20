package com.lambda.fusion.ai.chat.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = ChatSessionEntity.class)
@Data
@Schema(description = "创建会话DTO")
public class CreateSession extends BaseDTO<ChatSessionEntity> {
    private String title;
    private List<String> kbIds;
    private String llmModelId;
    private String robotId;
    private String systemPrompt;
}
