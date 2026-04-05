package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "提示词模板VO")
public class PromptTemplate {
    private String id;
    private String templateId;
    private String name;
    private String description;
    private String category;
    private String tags;
    private String templateContent;
    private String variables;
    private String exampleInput;
    private String exampleOutput;
    private String suggestedModel;
    private BigDecimal suggestedTemperature;
    private Integer suggestedMaxTokens;
    private String tenantId;
    private String ownerUserId;
    private Boolean isPublic;
    private Boolean isSystem;
    private Integer version;
    private String parentId;
    private Boolean enabled;
    private Long usageCount;
    private BigDecimal avgRating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
