package com.lambda.fusion.dict.model.entity;

import static com.lambda.fusion.dict.common.constants.DictConstants.*;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.cloud.core.shared.BaseDO;
import com.lambda.fusion.core.tree.Tree;
import com.lambda.fusion.dict.model.vo.DictInfoVO;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 字典表注释表
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
@TableName(value = TABLE_SYS_DICT_TYPE)
@EqualsAndHashCode(callSuper = true)
@Data
public class DictType extends BaseDO implements Tree<DictType> {

    @Schema(description = "主键")
    private String id;

    @Schema(description = "父id，0代表顶级")
    private String parentId = ROOT_PARENT_ID;

    @TableField(value = "dict_usage")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "字典用途，0：系统字典，1：用户字典")
    private Integer dictUsage;

    @Schema(description = "字典编码")
    private String dictType;

    @Schema(description = "字典名称")
    private String dictName;

    @TableField("level")
    @Schema(description = "级别：最顶层为1，后边层数累加", hidden = true)
    private Integer level = DEFAULT_LEVEL;

    @TableField("parentKeys")
    @Schema(description = "树的父节点", hidden = true)
    private String parentKeys;

    @Schema(description = "数据类型 0/null 静态类型 1 url  2 sql 3 enum")
    private Integer dataType;

    @Schema(description = "类型参数, 最大长度512")
    private String dataTypeValue;

    @Schema(description = "备注")
    private String notes;

    @Schema(description = "排序编码")
    private String sort;

    @Override
    public String id() {
        return id;
    }

    @Override
    public String pid() {
        return parentId;
    }

    @TableField(exist = false)
    @Schema(description = "子节点", hidden = true)
    private List<DictType> children;

    @TableField(exist = false)
    @Schema(description = "子节点数据", hidden = true)
    private List<DictInfoVO> data;

    @Override
    public void children(List<DictType> children) {
        this.children = children;
    }

    /**
     * 字典用途枚举
     */
    @Getter
    public enum DictUsage {

        /**
         * 系统字典
         */
        SYSTEM(0),

        /**
         * 用户字典
         */
        USER(1);

        /**
         * 字典用途值
         */
        private final int value;

        DictUsage(int value) {
            this.value = value;
        }
    }
}
