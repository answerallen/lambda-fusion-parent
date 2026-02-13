package com.lambda.fusion.authority.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.authority.organization.domain.Organization;
import com.lambda.fusion.authority.organization.mapper.OrganizationMapper;
import com.lambda.fusion.authority.role.mapper.GroupMapper;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.tenant.cache.TenantConfigurationCache;
import com.lambda.fusion.authority.tenant.cache.TenantHostCache;
import com.lambda.fusion.authority.tenant.event.*;
import com.lambda.fusion.authority.tenant.manager.TenantAuthorizeManager;
import com.lambda.fusion.authority.tenant.mapper.TenantMapper;
import com.lambda.fusion.authority.tenant.model.TenantEntity;
import com.lambda.fusion.authority.tenant.model.TenantOption;
import com.lambda.fusion.authority.tenant.service.TenantService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
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
public class TenantServiceImpl extends ServiceImpl<TenantMapper, TenantEntity>
        implements TenantService, ApplicationEventPublisherAware, CommandLineRunner {

    @Resource
    protected TenantMapper tenantMapper;

    @Resource
    protected RoleMapper roleMapper;

    @Resource
    private GroupMapper groupMapper;

    @Resource
    protected OrganizationMapper organizationMapper;

    //    @Autowired
    //    private ConfigService laConfigService;

    private final ObjectMapper objectMapper;

    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired(required = false)
    private TenantConfigurationCache configurationCache;

    @Autowired(required = false)
    private TenantHostCache tenantHostCache;

    @Autowired(required = false)
    private TenantAuthorizeManager tenantAuthorizeManager;

    /**
     * 租户平台配置类型值
     */
    public static final String CONFIG_TYPE_TENANT_SYSTEM = "tenant_system";

    /**
     * 租户可编辑配置类型值
     */
    @SuppressWarnings("unused")
    public static final String CONFIG_TYPE_TENANT_CUSTOM = "tenant_custom";

    /**
     * 租户配置为空时的默认值
     */
    private static final String TENANT_CONFIG_EMPTY_MAP = "{}";

    public TenantServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
            enabled = (Integer) 0;
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
        configurationCache.removeConfigCache(tenantId);

        switch (enabled) {
            case 0:
                // 禁用，发布事件
                applicationEventPublisher.publishEvent(new TenantDisabledEvent(tenantId));
                break;
            case 1:
                // 启用，发布事件
                applicationEventPublisher.publishEvent(new TenantEnabledEvent(tenantId));
                break;
            case -1:
                // 停用，发布事件
                applicationEventPublisher.publishEvent(new TenantDeactivatedEvent(tenantId));
                break;
            default:
                break;
        }
    }

    @Override
    public void examineTenant(LoginUser operator, Integer enabled, String tenantId) {
        // 判断当前用户是否有操作权限
        this.hasOperation(operator, tenantId);
        // 审核租户信息
        tenantMapper.examineTenantByTenantId(enabled, tenantId);
        if (enabled == 1) {
            // 审核通过，发布事件
            applicationEventPublisher.publishEvent(new TenantApprovedEvent(tenantId));
        }
        if (enabled == 0) {
            // 审核不通过，发布事件
            applicationEventPublisher.publishEvent(new TenantRejectedEvent(tenantId));
        }
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
        configurationCache.removeConfigCache(tenantId);
    }

    @Override
    @SneakyThrows
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(LoginUser operator, String tenantId, Map<String, Object> configMap) {
        Assert.notNull(tenantId, "租户ID不能为空！");
        Assert.isTrue(tenantMapper.isExist(tenantId), "租户不存在！");
        Assert.notNull(configMap, "lambda.authority.tenant.config.notempty");
        // 判断当前用户是否拥有操作权限
        this.hasOperation(operator, tenantId);

        // todo 过滤租户不能修改的项

        // todo 更新前需要先查询出原有的配置信息进行合并

        // todo 发布配置更新事件
        applicationEventPublisher.publishEvent(new TenantConfigurationChangedEvent(tenantId));
    }

    @Override
    public JsonNode getTenantConfigureById(LoginUser operator, String tenantId) {
        Assert.notNull(tenantId, "租户ID不能为空！");
        this.hasOperation(operator, tenantId);
        return getTenantConfigureById(tenantId);
    }

    @Override
    public Map<String, Object> getTenantConfigureMapById(String tenantId) {
        Assert.notNull(tenantId, "租户ID不能为空！");
        ObjectNode jsonNode = (ObjectNode) getTenantConfigureById(tenantId);
        Map<String, Object> map = new HashMap<>(jsonNode.size());
        jsonNode.properties().forEach(entry -> map.put(entry.getKey(), entry.getValue()));
        return map;
    }

    @Override
    public void initTenantMainDataBase(String tenantId, LoginUser operator) {
        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.initTenantMainDataBase(tenantId, operator);
        }
    }

    @SneakyThrows
    private JsonNode getTenantConfigureById(String tenantId) {
        String configJson = configurationCache.getConfigCache(tenantId);
        if (StringUtils.isNotBlank(configJson) && !TENANT_CONFIG_EMPTY_MAP.equals(configJson)) {
            // 返回缓存中的数据
            return objectMapper.readValue(configJson, ObjectNode.class);
        }
        TenantEntity tenant = this.getById(tenantId);
        Assert.notNull(tenant, "Tenant not found");
        configJson = tenant.getConfig();
        if (StringUtils.isBlank(configJson)) {
            configJson = TENANT_CONFIG_EMPTY_MAP;
        }
        ObjectNode jsonNode = objectMapper.readValue(configJson, ObjectNode.class);
        jsonNode.put("enabled", tenant.getEnabled());
        configurationCache.addConfigCache(tenantId, jsonNode);
        return jsonNode;
    }

    /**
     * 构造查询器
     *
     * @param parameters 查询参数
     * @return QueryWrapper<TenantEntity>
     */
    private QueryWrapper<TenantEntity> queryWrapper(Map<String, Object> parameters) {
        QueryWrapper<TenantEntity> query = new QueryWrapper<>();
        // 租户名称
        Optional.ofNullable(parameters.get("tenantName")).ifPresent(u -> query.like("TENANT_NAME", u));
        // 地址
        Optional.ofNullable(parameters.get("tenantAddress")).ifPresent(u -> query.like("TENANT_ADDRESS", u));
        // 法人
        Optional.ofNullable(parameters.get("legalPerson")).ifPresent(u -> query.eq("LEGAL_PERSON", u));
        query.orderByDesc("CREATED_AT");
        return query;
    }

    /**
     * 判断当前用户是否拥有操作权限
     *
     * @param operator 当前用户
     * @param tenantId 租户ID
     */
    protected void hasOperation(LoginUser operator, String tenantId) {
        String crrTenantId = operator.getTenantId();
        if (StringUtils.isNotBlank(crrTenantId)) {
            Assert.isTrue(crrTenantId.equals(tenantId), "当前租户ID与操作租户ID不一致");
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
        Assert.notNull(tenantId, "tenantId must not be null");
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

    @Override
    public void setApplicationEventPublisher(@Nonnull ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 初始化租户域名缓存
     *
     * @param args
     */
    @Override
    @SneakyThrows
    public void run(String... args) {
        if (tenantHostCache == null) {
            return;
        }
        tenantHostCache.clear();
        // 初始化租户域名映射缓存
        List<TenantEntity> tenantEntities = this.list();
        if (tenantEntities == null || tenantEntities.isEmpty()) {
            return;
        }

        // todo
    }
}
