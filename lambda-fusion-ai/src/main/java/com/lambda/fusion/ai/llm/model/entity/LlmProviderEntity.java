package com.lambda.fusion.ai.llm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.fusion.ai.llm.model.LlmProvider;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * LLM 提供商配置实体
 *
 * @author Jin
 */
@AutoConverter(target = LlmProvider.class)
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("ai_llm_provider")
@Schema(description = "LLM提供商实体")
public class LlmProviderEntity extends BaseEntity {

    @TableId(type = IdType.INPUT)
    @Schema(description = "提供商编码")
    private String code;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "租户ID")
    private String tenantId;
}
