package com.lambda.fusion.ai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "LLM提供商信息")
public class LlmProvider {

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

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
