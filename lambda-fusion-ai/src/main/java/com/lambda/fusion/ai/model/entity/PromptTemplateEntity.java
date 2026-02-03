package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 提示词模板实体类
 *
 * @author Jin
 */
@Data
@TableName("ai_prompt_template")
@Schema(description = "提示词模板实体")
public class PromptTemplateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

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
    private Long tenantId;
    private Long ownerUserId;
    private Boolean isPublic;
    private Boolean isSystem;
    private Integer version;
    private Long parentId;
    private Boolean enabled;
    private Long usageCount;
    private BigDecimal avgRating;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Long createdBy;
    private Long updatedBy;
}
