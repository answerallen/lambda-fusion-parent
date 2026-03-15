package com.lambda.fusion.authority.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.oss.manager.OssClientManager;
import com.lambda.cloud.oss.model.UploadObjectResult;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.mapper.OrganizationMapper;
import com.lambda.fusion.authority.mapper.RoleGroupMapper;
import com.lambda.fusion.authority.mapper.RoleMapper;
import com.lambda.fusion.authority.mapper.TenantMapper;
import com.lambda.fusion.authority.model.organization.Organization;
import com.lambda.fusion.authority.model.tenant.Tenant;
import com.lambda.fusion.authority.model.tenant.TenantEntity;
import com.lambda.fusion.authority.model.tenant.TenantOption;
import com.lambda.fusion.authority.service.TenantService;
import com.lambda.fusion.core.FusionConstants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/**
 * 租户信息表
 *
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class TenantServiceImpl extends ServiceImpl<TenantMapper, TenantEntity> implements TenantService {

    /**
     * 域名格式校验正则
     */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*\\.[a-zA-Z]{2,}$");

    @Resource
    protected TenantMapper tenantMapper;

    @Resource
    protected RoleMapper roleMapper;

    @Resource
    private RoleGroupMapper roleGroupMapper;

    @Resource
    protected OrganizationMapper organizationMapper;

    @Resource
    private OssClientManager ossClientManager;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Page<TenantEntity> pageTenant(Page<TenantEntity> pageable, LambdaQueryWrapper<TenantEntity> queryWrapper) {
        return baseMapper.selectPage(pageable, queryWrapper);
    }

    @Override
    public List<TenantOption> getTenantOptions() {
        return tenantMapper.queryTenantList();
    }

    @Override
    public TenantEntity createTenantWithLogo(String tenant, MultipartFile logo, String clientName) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity tenantEntity = toEntity(tenant);
        String tenantId = operator.getTenantId();
        tenantEntity.setOwner(tenantId);
        tenantEntity.setTenantId(IdWorker.getIdStr());
        applyTenantLogo(tenantEntity, logo, clientName);
        save(tenantEntity);
        return getById(tenantEntity.getTenantId());
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
    public void bindDomain(String tenantId, String domain) {
        // 校验域名格式
        if (StringUtils.isBlank(domain) || !DOMAIN_PATTERN.matcher(domain).matches()) {
            throw AuthorityBusinessException.tenantDomainInvalid(domain);
        }
        // 确认租户存在
        TenantEntity tenant = assertTenantExists(tenantId);
        String oldDomain = tenant.getTenantDomain();
        // 域名转小写标准化
        String normalizedDomain = domain.toLowerCase();
        // 检查域名唯一性（排除当前租户）
        if (tenantMapper.isDomainBound(normalizedDomain, tenant.getTenantId())) {
            throw AuthorityBusinessException.tenantDomainAlreadyBound(normalizedDomain);
        }
        // 更新域名
        tenant.setTenantDomain(normalizedDomain);
        updateById(tenant);

        // 同步 Redis
        if (StringUtils.isNotBlank(oldDomain) && !oldDomain.equals(normalizedDomain)) {
            redisTemplate.opsForHash().delete(FusionConstants.TENANT_HOST_REDIS_KEY, oldDomain);
        }
        redisTemplate.opsForHash().put(FusionConstants.TENANT_HOST_REDIS_KEY, normalizedDomain, tenantId);
    }

    @Override
    public void unbindDomain(String tenantId) {
        TenantEntity tenant = assertTenantExists(tenantId);
        String oldDomain = tenant.getTenantDomain();
        tenant.setTenantDomain(null);
        updateById(tenant);

        // 同步 Redis
        if (StringUtils.isNotBlank(oldDomain)) {
            redisTemplate.opsForHash().delete(FusionConstants.TENANT_HOST_REDIS_KEY, oldDomain);
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TenantEntity resolveByDomain(String domain) {
        if (StringUtils.isBlank(domain)) {
            throw AuthorityBusinessException.invalidParameter("域名不能为空");
        }
        TenantEntity tenant = tenantMapper.selectByDomain(domain.toLowerCase());
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(domain);
        }
        return tenant;
    }

    private void prohibitTenant(LoginUser operator, Integer status, String tenantId) {
        // 判断当前用户是否有操作权限
        this.hasOperation(operator, tenantId);
        // 禁用/启用租户
        tenantMapper.prohibitTenantByTenantId(status, tenantId);
        // 非启用状态下，都要禁用组织和角色
        Integer normalizedEnabled = normalizeEnabled(status);
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

    private void deleteTenant(LoginUser operator, String tenantId) {
        // 判断当前用户是否拥有操作权限
        this.hasOperation(operator, tenantId);

        TenantEntity tenant = getById(tenantId);
        if (tenant != null && StringUtils.isNotBlank(tenant.getTenantDomain())) {
            redisTemplate.opsForHash().delete(FusionConstants.TENANT_HOST_REDIS_KEY, tenant.getTenantDomain());
        }

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
        roleGroupMapper.deleteGroupByTenantId(tenantIds);
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

    private void handleTenantStatus(String tenantId, Integer status) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity tenant = assertTenantExists(tenantId);
        prohibitTenant(operator, status, tenant.getTenantId());
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
