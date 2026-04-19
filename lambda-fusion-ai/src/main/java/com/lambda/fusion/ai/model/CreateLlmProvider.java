package com.lambda.fusion.ai.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.ai.model.entity.LlmProviderEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@AutoConverter(target = LlmProviderEntity.class)
@Schema(description = "创建LLM提供商请求")
public class CreateLlmProvider extends BaseDTO<LlmProviderEntity> {

    @NotBlank(message = "提供商编码不能为空")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "提供商编码仅支持大写字母、数字和下划线")
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

    @Schema(description = "支持的模型类型")
    private List<String> modelTypes;
}
