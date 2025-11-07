package com.lambda.fusion.authority.organization.model.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.authority.organization.model.entity.OrganizationEntity;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
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
 * @author Jin
 */
@EqualsAndHashCode(callSuper = true)
@AutoConverter(target = OrganizationEntity.class)
@Data
@Schema(description = "组织元数据")
@NoArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP")
public class OrganizationCreateDTO extends BaseDTO<OrganizationEntity> {
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

    @TableField("ORG_RANK")
    private Integer rank;

    @Schema(description = "组织机构类型：部门为0, 租户为1")
    private Integer tenant = 0;

    @Schema(description = "是否启用: 0是禁用, 1是启用")
    private int enabled;

    @Schema(description = "组织类别")
    private Integer type;

    @Schema(description = "组织排序号")
    private int orderNo;
}
