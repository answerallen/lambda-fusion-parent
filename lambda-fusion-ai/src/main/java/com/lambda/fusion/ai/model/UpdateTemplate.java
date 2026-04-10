package com.lambda.fusion.ai.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新提示词模板DTO
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = PromptTemplateEntity.class)
@Data
@Schema(description = "创建提示词模板DTO")
public class UpdateTemplate extends BaseDTO<PromptTemplateEntity> {

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "模板描述")
    private String description;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "模板内容")
    private String templateContent;

    @Schema(description = "变量定义（JSON字符串）")
    private String variables;

    @Schema(description = "是否公开")
    private Boolean isPublic;
}
