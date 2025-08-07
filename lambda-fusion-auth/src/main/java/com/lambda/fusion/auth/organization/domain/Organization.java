package com.lambda.fusion.auth.organization.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lambda.fusion.core.tree.Tree;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.BooleanUtils;
import org.hibernate.validator.constraints.Length;

@Data
@Schema(description = "组织元数据")
@NoArgsConstructor
public class Organization implements Tree<Organization> {
    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "组织名")
    @JsonProperty("organization")
    @NotBlank
    @Length(max = 40)
    private String name;

    @Schema(description = "组织别名")
    @NotBlank
    @Length(max = 40)
    private String alias;

    @Schema(description = "父ID")
    private String parentId;

    @Length(max = 200)
    @Schema(description = "备注")
    private String remarks;

    @Schema(description = "级别：最顶层为1，后边层数累加")
    private Integer level;

    @Schema(description = "创建日期")
    private Date createDate;

    @Schema(description = "组织机构类型：部门为0, 租户为1")
    private Integer tenant = 0;

    @Schema(description = "子节点")
    private List<Organization> children;

    @Schema(description = "树的父节点", hidden = true)
    private String parentkeys;

    @Schema(description = "租户Id", hidden = true)
    private String tenantId;

    @Schema(description = "拥有者Id", hidden = true)
    private String owner;

    @Schema(description = "是否启用: 0是禁用, 1是启用")
    private int enabled;

    @Schema(description = "是否拥有操作权限")
    private Boolean noPermission;

    @Schema(description = "是否可以选中")
    private Boolean inAvailable;

    @Schema(description = "组织类别")
    private Integer type;

    @Schema(description = "父组织编码")
    @Length(max = 40)
    private String spid;

    @Schema(description = "组织排序号")
    private int orderNo;

    @Override
    public String id() {
        return this.getId();
    }

    @Override
    public String pid() {
        return this.getParentId();
    }

    @Override
    public String parentkeys() {
        return this.parentkeys;
    }

    @Override
    public void children(List<Organization> children) {
        this.setChildren(children);
    }

    public Organization(String id) {
        this.id = id;
    }

    public boolean typeOfTenant() {
        return BooleanUtils.toBoolean(this.tenant);
    }

    @Override
    public void pid(String pid) {
        setParentId(pid);
    }

    @Override
    public void parentkeys(String parentkeys) {
        setParentkeys(parentkeys);
    }

    @Override
    public int level() {
        return getLevel();
    }

    @Override
    public void level(int level) {
        setLevel(level);
    }

    @Override
    public int order() {
        return getOrderNo();
    }

    @Override
    public void order(int order) {
        setOrderNo(order);
    }
}
