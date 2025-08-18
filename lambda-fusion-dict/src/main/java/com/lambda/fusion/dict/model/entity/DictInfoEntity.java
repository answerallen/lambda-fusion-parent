package com.lambda.fusion.dict.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lambda.cloud.core.base.BaseDO;
import com.lambda.fusion.core.tree.Tree;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

import static com.lambda.fusion.dict.common.constants.DictConstants.*;

/**
 * 字典信息(值)
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
@EqualsAndHashCode(callSuper = true)
@TableName(value = TABLE_SYS_DICT_INFO)
@Data
public class DictInfoEntity extends BaseDO {
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

    @TableField("TENANT_ID")
    @Schema(description = "租户")
    private String tenantId;

    @Schema(description = "备注", example = "备注")
    private String notes;

    @TableField("extra")
    @Schema(description = "额外信息(文本入库使用)", hidden = true)
    private String extra;

    @TableField("parent_keys")
    @Schema(description = "树节点父节点")
    private String parentKeys;

    @TableField("level")
    @Schema(description = "级别：最顶层为1，后边层数累加", hidden = true)
    @JsonProperty("level")
    private Integer level = DEFAULT_LEVEL;
}
