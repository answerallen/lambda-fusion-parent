package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建提示词模板DTO")
public class CreateTemplate {
    @NotBlank
    private String name;

    private String description;
    private String category;

    @NotBlank
    private String templateContent;

    private String variables;
    private Long tenantId;
    private Long ownerUserId;
    private Boolean isPublic;
}
