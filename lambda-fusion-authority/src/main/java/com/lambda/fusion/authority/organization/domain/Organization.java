package com.lambda.fusion.authority.organization.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseVO;
import com.lambda.fusion.core.tree.TreeNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.BooleanUtils;
import org.hibernate.validator.constraints.Length;

/**
 * 组织机构实体类
 *
 * <p>表示系统中的组织机构信息，支持树形结构管理，包含部门和租户两种类型。
 * 实现了Tree接口，支持树形数据的构建和操作。
 *
 * <h3>功能特性：</h3>
 * <ul>
 * <li><strong>树形结构：</strong>支持多级组织架构，通过parentId和parentKeys维护层级关系</li>
 * <li><strong>多租户支持：</strong>支持租户隔离，通过tenant字段区分部门(0)和租户(1)</li>
 * <li><strong>权限控制：</strong>支持操作权限和选择权限的细粒度控制</li>
 * <li><strong>数据校验：</strong>集成Bean Validation，确保数据完整性</li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 * <li>企业组织架构管理</li>
 * <li>多租户系统的租户管理</li>
 * <li>权限系统的组织维度控制</li>
 * <li>用户归属组织的管理</li>
 * </ul>
 *
 * @author Lambda Fusion Team
 * @since 1.0.0
 * @see TreeNode 树形数据接口
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = OrganizationEntity.class)
@Data
@Schema(description = "组织元数据")
@NoArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP")
public class Organization extends BaseVO<OrganizationEntity> implements TreeNode<Organization> {
    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "组织名")
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
    private Integer category = 0;

    @Schema(description = "子节点")
    private List<Organization> children;

    @Schema(description = "树的父节点", hidden = true)
    private String parentKeys;

    @Schema(description = "租户Id", hidden = true)
    private String tenantId;

    @Schema(description = "拥有者Id", hidden = true)
    private String owner;

    @Schema(description = "是否启用: 0是禁用, 1是启用")
    private int enabled;

    @Schema(description = "是否拥有操作权限")
    private Boolean hasPermission;

    @Schema(description = "是否可以选中")
    private Boolean selectable;

    @Schema(description = "组织类别")
    private Integer type;

    @Schema(description = "组织排序号")
    @JsonProperty("sort")
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
    public String parentKeys() {
        return this.parentKeys;
    }

    @Override
    public void children(List<Organization> children) {
        this.setChildren(children);
    }

    public Organization(String id) {
        this.id = id;
    }

    /**
     * 判断当前组织是否为租户类型
     *
     * <p>根据tenant字段判断组织类型：
     * <ul>
     * <li>0 - 普通部门</li>
     * <li>1 - 租户组织</li>
     * </ul>
     *
     * @return true表示租户组织，false表示普通部门
     */
    public boolean typeOfTenant() {
        return BooleanUtils.toBoolean(this.category);
    }

    @Override
    public void pid(String pid) {
        setParentId(pid);
    }

    @Override
    public void parentKeys(String parentKeys) {
        setParentKeys(parentKeys);
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
