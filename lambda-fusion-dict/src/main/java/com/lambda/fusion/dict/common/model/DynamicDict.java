package com.lambda.fusion.dict.common.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author jin
 */
@Data
@Schema(description = "动态字典")
@NoArgsConstructor
public class DynamicDict {
    @Schema(description = "展示参数, 页面展示使用")
    private String key;

    @Schema(description = "映射参数, 持久化时使用")
    private Object val;

    @Schema(description = "可以被选择状态。0：只能用作显示，不能用于下拉选择，1：可以显示和下拉选择")
    private Integer selectable = 1;

    @Schema(description = "父级节点")
    private String pid;

    @Schema(description = "节点id")
    private String id;

    @Schema(description = "级别：最顶层为1，后边层数累加", hidden = true)
    private Integer level;

    public DynamicDict(String key, Object val) {
        this.key = key;
        this.val = val;
        this.selectable = 1;
    }

    public DynamicDict(String key, Object val, Integer selectable) {
        this.key = key;
        this.val = val;
        this.selectable = selectable;
    }
}
