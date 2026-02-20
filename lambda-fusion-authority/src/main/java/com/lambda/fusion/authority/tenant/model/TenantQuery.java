package com.lambda.fusion.authority.tenant.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.core.pagination.Pagination;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户分页查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "租户分页查询DTO")
public class TenantQuery extends Pagination<TenantEntity> {

    /**
     * 租户名称
     */
    @Schema(description = "租户名称")
    private String tenantName;

    /**
     * 租户地址
     */
    @Schema(description = "租户地址")
    private String tenantAddress;

    /**
     * 法人
     */
    @Schema(description = "法人")
    private String legalPerson;

    /**
     * 租户编码
     */
    @Schema(description = "租户编码")
    private String tenantCode;

    /**
     * 联系人
     */
    @Schema(description = "联系人")
    private String liaisonMan;

    /**
     * 联系电话
     */
    @Schema(description = "联系电话")
    private String liaisonPhone;

    /**
     * 是否启用，未启用:0,已启用:1,已停用:99
     */
    @Schema(description = "是否启用，未启用:0,已启用:1,已停用:99")
    private Integer enabled;

    /**
     * 审核状态，未审核:0,审核通过:1
     */
    @Schema(description = "审核状态，未审核:0,审核通过:1")
    private Integer examineState;

    /**
     * 拥有者
     */
    @Schema(description = "拥有者")
    private String owner;

    /**
     * 别名
     */
    @Schema(description = "别名")
    private String alias;

    /**
     * 地区
     */
    @Schema(description = "地区")
    private String prefecture;

    /**
     * 构建查询条件
     *
     * @return LambdaQueryWrapper查询条件
     */
    @Override
    public LambdaQueryWrapper<TenantEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<TenantEntity> wrapper = super.getLambdaQueryWrapper();

        // 租户名称模糊查询
        wrapper.like(StringUtils.isNotBlank(tenantName), TenantEntity::getTenantName, tenantName);

        // 租户地址模糊查询
        wrapper.like(StringUtils.isNotBlank(tenantAddress), TenantEntity::getTenantAddress, tenantAddress);

        // 法人精确查询
        wrapper.eq(StringUtils.isNotBlank(legalPerson), TenantEntity::getLegalPerson, legalPerson);

        // 租户编码精确查询
        wrapper.eq(StringUtils.isNotBlank(tenantCode), TenantEntity::getTenantCode, tenantCode);

        // 联系人模糊查询
        wrapper.like(StringUtils.isNotBlank(liaisonMan), TenantEntity::getLiaisonMan, liaisonMan);

        // 联系电话精确查询
        wrapper.eq(StringUtils.isNotBlank(liaisonPhone), TenantEntity::getLiaisonPhone, liaisonPhone);

        // 启用状态精确查询
        wrapper.eq(enabled != null, TenantEntity::getEnabled, enabled);

        // 审核状态精确查询
        wrapper.eq(examineState != null, TenantEntity::getExamineState, examineState);

        // 拥有者精确查询
        wrapper.eq(StringUtils.isNotBlank(owner), TenantEntity::getOwner, owner);

        // 别名模糊查询
        wrapper.like(StringUtils.isNotBlank(alias), TenantEntity::getAlias, alias);


        // 默认按创建时间降序排序
        wrapper.orderByDesc(TenantEntity::getCreatedAt);

        return wrapper;
    }
}
