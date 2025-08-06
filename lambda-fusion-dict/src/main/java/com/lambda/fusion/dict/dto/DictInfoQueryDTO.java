package com.lambda.fusion.dict.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典信息查询参数")
public class DictInfoQueryDTO {

    @Schema(description = "字典类型")
    private String dictType;

    @Schema(description = "字段类型")
    private String fieldType;

    @Schema(description = "字段名称")
    private String fieldName;

    @Schema(description = "父级ID")
    private String parentId;

    @Schema(description = "启用状态")
    private Integer enableState;

    @Schema(description = "可选择状态")
    private Integer selectable;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "扩展参数")
    private Map<String, Object> extraParams;
}
