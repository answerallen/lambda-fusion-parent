package com.lambda.fusion.dict.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author Jin
 */
@Data
@Schema(description = "查询字典树形数据")
public class QueryDictTree {
    @Schema(description = "字典类型")
    private String type;

    @Schema(description = "字典名称")
    private String name;

    @Schema(description = " 数据类型 0/null: 静态类型, 1: url,  2: sql, 3: enum")
    private Integer dataType;

    @Schema(description = "是否只查询用户字典")
    private boolean userOnly;
}
