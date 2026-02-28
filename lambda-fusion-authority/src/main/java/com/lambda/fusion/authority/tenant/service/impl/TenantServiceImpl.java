package com.lambda.fusion.authority.tenant.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.oss.manager.OssClientManager;
import com.lambda.cloud.oss.model.UploadObjectResult;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.organization.domain.Organization;
import com.lambda.fusion.authority.organization.mapper.OrganizationMapper;
import com.lambda.fusion.authority.role.mapper.GroupMapper;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.tenant.mapper.TenantMapper;
import com.lambda.fusion.authority.tenant.model.Tenant;
import com.lambda.fusion.authority.tenant.model.TenantEntity;
import com.lambda.fusion.authority.tenant.model.TenantOption;
import com.lambda.fusion.authority.tenant.model.TenantQuery;
import com.lambda.fusion.authority.tenant.service.TenantService;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 租户信息表
 *
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class TenantServiceImpl extends ServiceImpl<TenantMapper, TenantEntity> implements TenantService {

    @Resource
    protected TenantMapper tenantMapper;

    @Resource
    protected RoleMapper roleMapper;

    @Resource
    private GroupMapper groupMapper;

    @Resource
    protected OrganizationMapper organizationMapper;

    @Resource
    private OssClientManager ossClientManager;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Page<TenantEntity> page(Page<TenantEntity> pageable, LambdaQueryWrapper<TenantEntity> queryWrapper) {
        return baseMapper.selectPage(pageable, queryWrapper);
    }

    @Override
    public Page<TenantEntity> pageTenant(TenantQuery queryDTO) {
        return page(queryDTO.getPage(), queryDTO.getLambdaQueryWrapper());
    }

    @Override
    public List<TenantOption> getTenantOptions() {
        return tenantMapper.queryTenantList();
    }

    @Override
    public TenantEntity createTenantWithLogo(String tenant, MultipartFile logo, String clientName) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = toEntity(tenant);
        String id = IdWorker.getIdStr();
        String tenantId = operator.getTenantId();
        target.setOwner(tenantId);
        target.setTenantId(id);
        applyTenantLogo(target, logo, clientName);
        save(target);
        return getById(target.getTenantId());
    }


    @Override
    public TenantEntity updateTenantWithLogo(String id, String tenant, MultipartFile logo, String clientName) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = toEntity(tenant);
        target.setTenantId(id);
        target.setUpdatedBy(operator.getName());
        applyTenantLogo(target, logo, clientName);
        updateById(target);
        return getById(id);
    }

    @Override
    public void deleteTenant(String tenantId) {
        LoginUser operator = OperatorUtils.getOperator();
        deleteTenant(operator, tenantId);
    }

    @Override
    public void enableTenant(String tenantId) {
        handleTenantStatus(tenantId, 1);
    }

    @Override
    public void disableTenant(String tenantId) {
        handleTenantStatus(tenantId, 0);
    }

    @Override
    public void stopTenant(String tenantId) {
        handleTenantStatus(tenantId, -1);
    }

    @Override
    public void examineTenant(String tenantId) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity tenant = assertTenantExists(tenantId);
        examineTenant(operator, 1, tenant.getTenantId());
    }

    @Override
    public void prohibitTenant(LoginUser operator, Integer enabled, String tenantId) {
        // 判断当前用户是否有操作权限
        this.hasOperation(operator, tenantId);
        // 禁用/启用租户
        tenantMapper.prohibitTenantByTenantId(enabled, tenantId);
        // 非启用状态下，都要禁用组织和角色
        Integer normalizedEnabled = normalizeEnabled(enabled);
        final List<Organization> orgIds = queryOrganizationByTenantId(tenantId);
        final List<String> tenantSubOrganizationIds = getSubOrgIdsByType(orgIds, true);
        final List<String> subOrganizationIds = getSubOrgIdsByType(orgIds, false);
        final List<String> ids = extractOrgIds(orgIds);
        tenantSubOrganizationIds.add(tenantId);
        if (CollectionUtils.isNotEmpty(ids)) {
            // 禁用/启用组织
            organizationMapper.updateEnabledOrganizationByIds(normalizedEnabled, ids);
        }
        // 禁用/启用用户角色
        prohibitOrgUsersByTenantOrgan(normalizedEnabled, tenantSubOrganizationIds);
        prohibitOrgUsersByOrdinaryOrgan(normalizedEnabled, subOrganizationIds);
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
        final List<String> ids = extractOrgIds(orgIds);
        if (CollectionUtils.isNotEmpty(ids)) {
            // 删除组织
            organizationMapper.deleteOrgByIdList(ids);
            // 删除用户组织
            organizationMapper.deleteUserOrgByIdList(ids);
        }
        // 删除分组
        List<String> tenantIds = List.of(tenantId);
        groupMapper.deleteGroupByTenantId(tenantIds);
        // 删除角色权限
        roleMapper.deleteRoleAuthorizeByTenantId(tenantIds);
        // 删除角色
        roleMapper.deleteRoleByTenantId(tenantIds);
    }

    protected void hasOperation(LoginUser operator, String tenantId) {
        String crrTenantId = operator.getTenantId();
        if (StringUtils.isNotBlank(crrTenantId) && !crrTenantId.equals(tenantId)) {
            throw AuthorityBusinessException.authNoPermission();
        }
    }

    protected void prohibitOrgUsersByTenantOrgan(Integer enabled, List<String> ids) {
        if (CollectionUtils.isNotEmpty(ids)) {
            organizationMapper.updateEnabledRoleByOrganizationByIds(enabled, ids);
            organizationMapper.updateEnabledOrganizationUsersByTenantIds(enabled, ids);
        }
    }

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

    private Integer normalizeEnabled(Integer enabled) {
        return enabled != null && enabled == 1 ? 1 : 0;
    }

    private List<String> extractOrgIds(List<Organization> orgIds) {
        if (CollectionUtils.isEmpty(orgIds)) {
            return new ArrayList<>();
        }
        return orgIds.stream().map(Organization::getId).collect(Collectors.toList());
    }

    private TenantEntity toEntity(String payload) {
        return readTenant(payload).toEntity();
    }

    private void applyTenantLogo(TenantEntity target, MultipartFile logo, String clientName) {
        if (logo != null && !logo.isEmpty()) {
            UploadObjectResult uploadObjectResult = uploadLogo(logo, clientName);
            target.setTenantLogo(uploadObjectResult.getUrl());
        }
    }

    private void handleTenantStatus(String tenantId, Integer enabled) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity tenant = assertTenantExists(tenantId);
        prohibitTenant(operator, enabled, tenant.getTenantId());
    }

    private TenantEntity assertTenantExists(String tenantId) {
        if (tenantId == null) {
            throw AuthorityBusinessException.invalidParameter("租户编号不能为空");
        }
        TenantEntity tenant = getById(tenantId);
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(tenantId);
        }
        return tenant;
    }

    private UploadObjectResult uploadLogo(MultipartFile file, String clientName) {
        try {
            if (file == null || file.isEmpty()) {
                throw AuthorityBusinessException.invalidParameter("文件不能为空");
            }
            String ext = FileUtil.extName(file.getOriginalFilename());
            String suffix = (ext != null && !ext.isBlank()) ? "." + ext : "";
            String objectKey = "tenant/logo/" + IdWorker.getIdStr() + suffix;
            return ossClientManager.get(clientName).upload(file.getInputStream(), objectKey, file.getContentType());
        } catch (Exception e) {
            throw AuthorityBusinessException.systemError("上传失败：" + e.getMessage());
        }
    }

    private Tenant readTenant(String payload) {
        try {
            return objectMapper.readValue(payload, Tenant.class);
        } catch (Exception e) {
            throw AuthorityBusinessException.invalidParameter("tenant解析失败：" + e.getMessage());
        }
    }
}
