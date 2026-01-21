package com.lambda.fusion.dict.model;

import static com.lambda.fusion.dict.support.constants.DictConstants.*;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lambda.cloud.core.shared.BaseDO;
import com.lambda.fusion.core.tree.TreeNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典信息(值)
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = TABLE_SYS_DICT_INFO)
public class DictionaryEntry extends BaseDO implements TreeNode<DictionaryEntry> {
    @TableId
    @Schema(description = "id")
    private String id;

    @TableField("dict_type")
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "字典类型")
    private String dictType;

    @TableField(exist = false)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "字典名称")
    private String dictName;

    @TableField("field_type")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "字段类型")
    private String fieldType;

    @TableField("field_name")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "字段描述")
    private String fieldName;

    @TableField("sort")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "排序（顺序越小越靠前，0-999999）", example = "0")
    private String sort;

    @TableField("enable_state")
    @Schema(description = "启用状态（0未启用，1启用）", example = "1")
    private Integer enableState = ENABLE_STATE_ENABLED;

    @TableField("select_able")
    @Schema(description = "可选择状态（0不能用于下拉列表，1可以用于下拉列表）", example = "1", defaultValue = "1")
    private Integer selectable = SELECTABLE_ENABLED;

    @TableField("parent_id")
    @Schema(description = "父节点")
    private String parentId;

    @TableField("tenant_id")
    @Schema(description = "租户")
    private String tenantId;

    @Schema(description = "备注", example = "备注")
    private String notes;

    @JsonIgnore
    @TableField("extra")
    @Schema(description = "额外信息(文本入库使用)", hidden = true)
    private String extra;

    @TableField(exist = false)
    @Schema(description = "额外信息详情")
    private Map<String, Object> parameters;

    @TableField("parent_keys")
    @Schema(description = "树节点父节点")
    private String parentKeys;

    @TableField("level")
    @Schema(description = "级别：最顶层为1，后边层数累加", hidden = true)
    @JsonProperty("level")
    private Integer level = DEFAULT_LEVEL;

    @TableField(exist = false)
    @Schema(description = "子节点", hidden = true)
    private List<DictionaryEntry> children;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return parentId;
    }

    @Override
    public void children(List<DictionaryEntry> children) {
        this.children = children;
    }
}
