package com.lambda.fusion.authority.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.organization.domain.Organization;
import com.lambda.fusion.authority.organization.mapper.OrganizationMapper;
import com.lambda.fusion.authority.role.mapper.GroupMapper;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.tenant.mapper.TenantMapper;
import com.lambda.fusion.authority.tenant.model.TenantEntity;
import com.lambda.fusion.authority.tenant.model.TenantOption;
import com.lambda.fusion.authority.tenant.service.TenantService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户信息表
 *
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class TenantServiceImpl extends ServiceImpl<TenantMapper, TenantEntity> implements TenantService {

    @Resource
    protected TenantMapper tenantMapper;

    @Resource
    protected RoleMapper roleMapper;

    @Resource
    private GroupMapper groupMapper;

    @Resource
    protected OrganizationMapper organizationMapper;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Page<TenantEntity> page(Page<TenantEntity> pageable, LambdaQueryWrapper<TenantEntity> queryWrapper) {
        return baseMapper.selectPage(pageable, queryWrapper);
    }

    @Override
    public List<TenantOption> getTenantOptions() {
        return tenantMapper.queryTenantList();
    }

    @Override
    public void prohibitTenant(LoginUser operator, Integer enabled, String tenantId) {
        // 判断当前用户是否有操作权限
        this.hasOperation(operator, tenantId);
        // 禁用/启用租户
        tenantMapper.prohibitTenantByTenantId(enabled, tenantId);
        // 非启用状态下，都要禁用组织和角色
        if (enabled != 1) {
            enabled = 0;
        }
        final List<Organization> orgIds = queryOrganizationByTenantId(tenantId);
        final List<String> tenants = getSubOrgIdsByType(orgIds, true);
        final List<String> ordinaries = getSubOrgIdsByType(orgIds, false);
        final List<String> ids = orgIds.stream().map(Organization::getId).collect(Collectors.toList());
        tenants.add(tenantId);
        if (CollectionUtils.isNotEmpty(ids)) {
            // 禁用/启用组织
            organizationMapper.updateEnabledOrganizationByIds(enabled, ids);
        }
        // 禁用/启用用户角色
        prohibitOrgUsersByTenantOrgan(enabled, tenants);
        prohibitOrgUsersByOrdinaryOrgan(enabled, ordinaries);
    }

    @Override
    public void examineTenant(LoginUser operator, Integer enabled, String tenantId) {
        // 判断当前用户是否有操作权限
        this.hasOperation(operator, tenantId);
        // 审核租户信息
        tenantMapper.examineTenantByTenantId(enabled, tenantId);
    }

    @Override
    public void deleteTenant(LoginUser operator, String tenantId) {
        // 判断当前用户是否拥有操作权限
        this.hasOperation(operator, tenantId);
        // 删除租户
        tenantMapper.deleteById(tenantId);
        // 通过租户编号查询组织，删除组织
        final List<Organization> orgIds = queryOrganizationByTenantId(tenantId);
        final List<String> ids = orgIds.stream().map(Organization::getId).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(ids)) {
            // 删除组织
            organizationMapper.deleteOrgByIdList(ids);
            // 删除用户组织
            organizationMapper.deleteUserOrgByIdList(ids);
        }
        // 删除分组
        List<String> tenantIds = new ArrayList<>();
        tenantIds.add(tenantId);
        groupMapper.deleteGroupByTenantId(tenantIds);
        // 删除角色权限
        roleMapper.deleteRoleByTenantId(tenantIds);
        // 删除角色
        roleMapper.deleteRoleByTenantId(tenantIds);
    }

    /**
     * 判断当前用户是否拥有操作权限
     *
     * @param operator 当前用户
     * @param tenantId 租户ID
     */
    protected void hasOperation(LoginUser operator, String tenantId) {
        String crrTenantId = operator.getTenantId();
        if (StringUtils.isNotBlank(crrTenantId) && !crrTenantId.equals(tenantId)) {
            throw AuthorityBusinessException.authNoPermission();
        }
    }

    /**
     * "租户"类型机构 禁用/启用组织机构下用户
     *
     * @param enabled 禁用/启用
     * @param ids     组织id
     */
    protected void prohibitOrgUsersByTenantOrgan(Integer enabled, List<String> ids) {
        if (CollectionUtils.isNotEmpty(ids)) {
            organizationMapper.updateEnabledRoleByOrganizationByIds(enabled, ids);
            organizationMapper.updateEnabledOrganizationUsersByTenantIds(enabled, ids);
        }
    }

    /**
     * "普通"类型机构 禁用/启用组织机构下用户
     *
     * @param enabled 禁用/启用
     * @param ids     组织id
     */
    protected void prohibitOrgUsersByOrdinaryOrgan(Integer enabled, List<String> ids) {
        if (CollectionUtils.isNotEmpty(ids)) {
            organizationMapper.updateEnabledOrganizationUsersByOrgIds(enabled, ids);
        }
    }

    protected List<Organization> queryOrganizationByTenantId(String tenantId) {
        if (tenantId == null) {
            throw AuthorityBusinessException.invalidParameter("租户ID不能为空");
        }
        return organizationMapper.selectOrganizationByTenantId(tenantId);
    }

    protected List<String> getSubOrgIdsByType(List<Organization> orgIds, Boolean isTenant) {
        if (CollectionUtils.isNotEmpty(orgIds)) {
            return orgIds.stream()
                    .filter(org -> BooleanUtils.toBoolean(org.getCategory()) == isTenant)
                    .map(Organization::id)
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }
}
