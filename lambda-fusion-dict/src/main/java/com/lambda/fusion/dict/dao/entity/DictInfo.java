package com.lambda.fusion.dict.dao.entity;

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
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 字典信息(值)
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_dict_info")
@Data
public class DictInfo extends BaseDO implements Tree<DictInfo> {
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
    private Integer enableState;

    @TableField("select_able")
    @Schema(description = "可选择状态（0不能用于下拉列表，1可以用于下拉列表）", example = "1", defaultValue = "1")
    private Integer selectable = 1;

    @TableField("parentid")
    @Schema(description = "父节点")
    private String parentId;

    @TableField("TENANTID")
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

    @TableField("parentkeys")
    @Schema(description = "树节点父节点")
    private String parentkeys;

    @TableField("level")
    @Schema(description = "级别：最顶层为1，后边层数累加", hidden = true)
    @JsonProperty("level")
    private Integer level = 1;

    @TableField(exist = false)
    @Schema(description = "子节点", hidden = true)
    private List<DictInfo> children;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return parentId;
    }

    @Override
    public void children(List<DictInfo> children) {
        this.children = children;
    }

    @Getter
    @Setter
    public static class Additional {

        private Map<String, Object> parameters;
    }
}
