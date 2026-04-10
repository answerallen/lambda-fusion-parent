package com.lambda.fusion.ai.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = PromptTemplateEntity.class)
@Data
@Schema(description = "创建提示词模板DTO")
public class CreateTemplate extends BaseDTO<PromptTemplateEntity> {
    @NotBlank
    private String name;

    private String description;
    private String category;

    @NotBlank
    private String templateContent;

    private String variables;
    private String tenantId;
    private String ownerUserId;
    private Boolean isPublic;
}
