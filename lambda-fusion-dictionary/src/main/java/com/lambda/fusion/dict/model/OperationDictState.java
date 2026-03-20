package com.lambda.fusion.dict.model;

import com.lambda.fusion.dict.commons.OperationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典状态操作参数")
public class OperationDictState {

    @NotBlank(message = "字典ID不能为空")
    @Schema(description = "字典ID")
    private String id;

    @NotNull(message = "状态值不能为空")
    @Schema(description = "状态值")
    private Integer state;

    @Schema(description = "操作类型: ENABLE_STATE, SELECTABLE")
    private OperationType operationType;
}
