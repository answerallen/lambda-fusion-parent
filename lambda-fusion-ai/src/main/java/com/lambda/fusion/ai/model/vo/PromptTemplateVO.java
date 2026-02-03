package com.lambda.fusion.ai.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "提示词模板VO")
public class PromptTemplateVO {
    private Long id;
    private String templateId;
    private String name;
    private String description;
    private String category;
    private String templateContent;
    private String variables;
    private Boolean isPublic;
    private Boolean isSystem;
    private Long usageCount;
    private BigDecimal avgRating;
    private LocalDateTime createdAt;
}
