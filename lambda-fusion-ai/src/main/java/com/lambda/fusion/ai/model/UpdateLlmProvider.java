package com.lambda.fusion.ai.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.ai.model.entity.LlmProviderEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@AutoConverter(target = LlmProviderEntity.class)
@Schema(description = "更新LLM提供商请求")
public class UpdateLlmProvider extends BaseDTO<LlmProviderEntity> {

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "支持的模型类型")
    private List<String> modelTypes;
}
