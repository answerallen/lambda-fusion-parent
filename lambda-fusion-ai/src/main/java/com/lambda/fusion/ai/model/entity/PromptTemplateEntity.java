package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.model.PromptDefinition;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 提示词模板实体类
 *
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = PromptDefinition.class)
@Data
@TableName("ai_prompt_template")
@Schema(description = "提示词模板实体")
public class PromptTemplateEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

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
}
