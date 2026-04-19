package com.lambda.fusion.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型类型与提供商绑定关系实体
 *
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("ai_llm_model_type_provider")
@Schema(description = "模型类型-提供商绑定关系实体")
public class LlmModelTypeProviderEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "关系ID")
    private String id;

    @Schema(description = "模型类型")
    private String modelType;

    @Schema(description = "提供商编码")
    private String providerCode;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "租户ID")
    private String tenantId;
}
