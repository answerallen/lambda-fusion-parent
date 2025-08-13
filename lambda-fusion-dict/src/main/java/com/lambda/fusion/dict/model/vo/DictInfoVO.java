package com.lambda.fusion.dict.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author Jin
 */
@Data
@Schema(description = "更新数据字典对象")
public class DictInfoVO {

    @NotBlank
    String id;

    @Max(200)
    String fieldName;

    @NotNull
    Integer sort;

    @NotNull
    Integer enableState;

    @NotNull
    Integer selectable;

    @NotBlank
    String fieldType;

    String remarks;

    @NotBlank
    @Max(30)
    private String dictType;
}
