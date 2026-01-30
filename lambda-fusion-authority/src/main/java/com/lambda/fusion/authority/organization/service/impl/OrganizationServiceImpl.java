package com.lambda.fusion.authority.organization.service.impl;

import static com.lambda.fusion.core.FusionConstants.FUZZY;
import static com.lambda.fusion.core.FusionConstants.JOINER;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.organization.domain.*;
import com.lambda.fusion.authority.organization.mapper.OrganizationMapper;
import com.lambda.fusion.authority.organization.mapper.UserOrganizationMapper;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.resource.model.MoveResource;
import com.lambda.fusion.authority.role.mapper.GroupMapper;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.model.Role;
import com.lambda.fusion.authority.role.service.RoleService;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.model.User;
import com.lambda.fusion.autoconfig.AuthorityProperties;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserPrincipal;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.tree.model.TreeDragMode;
import com.lambda.fusion.core.tree.util.TreeNodeUtils;
import com.lambda.fusion.core.utils.LoginUserUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
public class OrganizationServiceImpl implements OrganizationService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;
    private final OrganizationMapper organizationMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final GroupMapper groupMapper;
    private final AuthorityProperties authorityProperties;

    @Override
    public OrganizationQuery getOrganizationQuery() {
        OrganizationQuery parameters = new OrganizationQuery();
        String tenantId = OperatorUtils.getOperator().getTenantId();
        parameters.setOwner(StringUtils.isNotBlank(tenantId) ? tenantId : null);
        return parameters;
    }

    @Override
    public List<Organization> treeList(OrganizationQuery organizationQuery) {
        UserPrincipal userPrincipal = LoginUserUtils.getLoginUser();
        List<Organization> organizations = getOrganizationsByCondition(userPrincipal, organizationQuery);
        applyPermissionConstraints(organizations, userPrincipal);
        return TreeBuilder.build(organizations);
    }

    /**
     * 根据条件获取组织列表
     *
     * @param userPrincipal 操作用户
     * @param parameters    查询参数
     * @return 组织列表
     */
    private List<Organization> getOrganizationsByCondition(UserPrincipal userPrincipal, OrganizationQuery parameters) {
        if (hasSearchCondition(parameters)) {
            return getOrganizationsBySearchCondition(userPrincipal, parameters);
        } else {
            return getSubOrganizationIds(parameters);
        }
    }

    /**
     * 检查是否有搜索条件
     */
    private boolean hasSearchCondition(OrganizationQuery parameters) {
        return StringUtils.isNotBlank(parameters.getAlias()) || StringUtils.isNotBlank(parameters.getName());
    }

    /**
     * 根据搜索条件获取组织列表
     */
    private List<Organization> getOrganizationsBySearchCondition(
            UserPrincipal principal, OrganizationQuery parameters) {
        List<Organization> list = getOrgByCondition(principal, parameters);

        if (principal.isAdmin()) {
            Set<String> additionalOrgIds = collectAdditionalOrgIds(principal.getOrgId(), list);
            if (CollectionUtils.isNotEmpty(additionalOrgIds)) {
                parameters.setIds(new ArrayList<>(additionalOrgIds));
                list.addAll(organizationMapper.getOrganizations(parameters));
            }
        }

        return list.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 收集额外的组织ID（父级和子级）
     */
    private Set<String> collectAdditionalOrgIds(String orgId, List<Organization> list) {
        Set<String> orgIds = new HashSet<>();
        for (Organization org : list) {
            addParentOrgIds(orgId, org, orgIds, true);
            orgIds.addAll(getChildrenById(org.getId()));
        }
        return orgIds;
    }

    /**
     * 添加父级组织ID
     */
    private void addParentOrgIds(String orgId, Organization org, Set<String> orgIds, boolean isAdmin) {
        String parentKeys = org.getParentKeys();
        if (StringUtils.isBlank(parentKeys)) {
            return;
        }

        String[] split = StringUtils.split(parentKeys, JOINER);
        List<String> ids = new ArrayList<>(Arrays.asList(split));

        if (isAdmin && StringUtils.isNotBlank(orgId)) {
            String substring = StringUtils.substring(parentKeys, StringUtils.indexOf(parentKeys, orgId));
            split = StringUtils.split(substring, JOINER);
            ids = new ArrayList<>(Arrays.asList(split));
        }

        orgIds.addAll(ids);
    }

    /**
     * 应用权限约束
     */
    private void applyPermissionConstraints(List<Organization> organizations, UserPrincipal userPrincipal) {
        String operatorOrgId = userPrincipal.getOrgId();
        String operatorTenantId = userPrincipal.getTenantId();

        for (Organization org : organizations) {
            // 设置操作权限
            if (!userPrincipal.isAdmin() && org.getId().equals(operatorOrgId)) {
                org.setNoPermission(true);
            }

            // 设置租户权限
            if (!Objects.equals(operatorTenantId, org.getOwner())) {
                org.setNoPermission(true);
                org.setInAvailable(true);
            }
        }
    }

    @Override
    public List<Organization> getSubOrganizationIds(OrganizationQuery parameters) {
        LoginUser operator = OperatorUtils.getOperator();
        List<String> orgIds = getSubOrganizationIds(operator);
        if (CollectionUtils.isNotEmpty(orgIds)) {
            parameters.setIds(orgIds);
        }
        return organizationMapper.getOrganizations(parameters);
    }

    public List<Organization> getOrgByCondition(LoginUser operator, OrganizationQuery parameters) {
        List<String> orgIds = getSubOrganizationIds(operator);
        if (CollectionUtils.isNotEmpty(orgIds)) {
            parameters.setIds(orgIds);
        }
        return organizationMapper.getOrgIdsByCondition(parameters);
    }

    @Override
    public List<Organization> selectAll(OrganizationQuery parameters) {
        return organizationMapper.getOrganizations(parameters);
    }

    @Override
    public List<OrganizationTree> getSimpleOrgTree(OrganizationQuery parameters) {
        List<OrganizationTree> list = organizationMapper.getEnabledOrganization(parameters);
        return TreeBuilder.build(list);
    }

    @Override
    public Organization queryOrganizationById(String id) {
        return getOrganizationById(id);
    }

    private Organization getOrganizationById(String id) {
        return organizationMapper.queryOrganizationById(id);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteOrganization(String id) {
        LoginUser operator = OperatorUtils.getOperator();

        // 权限检查
        validateDeletePermission(operator, id);

        // 获取组织信息
        Organization organization = getOrganizationById(id);
        Assert.notNull(organization, "组织不存在！");

        // 检查删除前置条件
        validateDeleteConditions(id);

        // 执行删除操作
        performDeletion(organization, id);
    }

    /**
     * 验证删除权限
     */
    private void validateDeletePermission(LoginUser operator, String id) {
        this.hasOperation(operator, id);
    }

    /**
     * 验证删除前置条件
     */
    private void validateDeleteConditions(String id) {
        List<String> childIds = getChildrenById0(id);
        Assert.state(CollectionUtils.isEmpty(childIds), "存在子组织，无法删除");

        List<String> allIds = new ArrayList<>(childIds);
        allIds.add(id);

        boolean hasUser = organizationMapper.existUser(allIds);
        Assert.state(!hasUser, "组织下存在用户，无法删除");
    }

    /**
     * 执行删除操作
     */
    private void performDeletion(Organization organization, String id) {
        if (BooleanUtils.toBoolean(organization.getTenant())) {
            deleteTenantRelatedData(id);
        }

        List<String> ids = Collections.singletonList(id);
        groupMapper.deleteByOrgIds(ids);
        organizationMapper.deleteById(id);
        userOrganizationMapper.deleteUserOrganizationByOrg(id);
    }

    /**
     * 删除租户相关数据
     */
    private void deleteTenantRelatedData(String tenantId) {
        List<Role> roles = roleMapper.getTenantRolesByOwner(tenantId);
        if (CollectionUtils.isNotEmpty(roles)) {
            for (Role role : roles) {
                if (!BooleanUtils.toBoolean(role.getRoleType())) {
                    roleService.deleteRoleById(role.getAuthority());
                }
            }
        }

        organizationMapper.delete(
                new LambdaQueryWrapper<OrganizationEntity>().eq(OrganizationEntity::getOwner, tenantId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Organization updateOrganization(UpdateOrganization resource) {
        LoginUser operator = OperatorUtils.getOperator();
        hasOperation(operator, resource.getId());
        Assert.notNull(resource.getId(), "机构ID不能为空");
        List<OrganizationEntity> organizations =
                organizationMapper.selectList(new LambdaQueryWrapper<OrganizationEntity>()
                        .eq(OrganizationEntity::getName, resource.getName())
                        .eq(OrganizationEntity::getOwner, operator.getTenantId()));
        Assert.isTrue(CollectionUtils.isEmpty(organizations), "lambda.authority.organ.name.repeat");
        OrganizationEntity organizationEntity = resource.toEntity();
        organizationMapper.updateById(organizationEntity);
        return getOrganizationById(resource.getId());
    }

    @Override
    public List<OrganizationWithUser> getAllOrganMutableUsers(List<User> users) {
        return organizationMapper.getOrganizationUsers(users);
    }

    @Override
    public UserOrganization queryUserOrganization(UserOrganizationChange resource) {
        Assert.notNull(resource, "organ must not be null");
        Assert.notNull(resource.getUsername(), "user id must not be null");
        UserOrganizationEntity userOrganizationEntity =
                userOrganizationMapper.selectUserOrganization(resource.getUsername());
        return UserOrganization.fromEntity(UserOrganization.class, userOrganizationEntity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UserOrganization addUserOrganization(UserOrganizationChange userOrganizationDTO) {
        Assert.notNull(userOrganizationDTO, "organ must not be null");
        Assert.notNull(userOrganizationDTO.getUsername(), "user id must not be null");
        Assert.notNull(userOrganizationDTO.getOrganizationId(), "organization id must not be null");
        Assert.notNull(
                userMapper.selectUserByUsername(userOrganizationDTO.getUsername()),
                "lambda.authority.organ.user.notfound");
        Assert.notNull(getOrganizationById(userOrganizationDTO.getOrganizationId()), "机构不存在！");
        UserOrganizationEntity userOrganization = userOrganizationDTO.toEntity();
        userOrganizationMapper.insert(userOrganization);
        return UserOrganization.fromEntity(UserOrganization.class, userOrganization);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteUserOrganization(String username) {
        userOrganizationMapper.deleteUserOrganizationByUser(username);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UserOrganization updateUserOrganization(UserOrganizationChange resource) {
        Assert.notNull(resource.getUsername(), "user id must not be null");
        Assert.notNull(resource.getOrganizationId(), "organization id must not be null");
        Organization organization = getOrganizationById(resource.getOrganizationId());
        Assert.notNull(organization, "机构不存在！");
        UserOrganizationEntity userOrganization = resource.toEntity();
        userOrganizationMapper.updateById(userOrganization);
        return UserOrganization.fromEntity(UserOrganization.class, userOrganization);
    }

    @Override
    public List<String> getParentsById(String id) {
        Assert.notNull(id, "id must not be null");
        Organization organ = organizationMapper.queryOrganizationById(id);
        if (organ != null) {
            String parentKeys = organ.getParentKeys();
            if (StringUtils.isNotBlank(parentKeys)) {
                return Lists.newArrayList(parentKeys.split(JOINER));
            }
        }
        return new ArrayList<>(0);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void prohibitOrganization(Integer enabled, String id) {
        Assert.notNull(id, "id is not empty");
        Organization org = getOrganizationById(id);
        Assert.notNull(org, "org is not null");
        LoginUser operator = OperatorUtils.getOperator();
        this.hasOperation(operator, id);
        final List<Organization> orgIds = getSubOrgIds(id);
        final List<String> tenants = getSubOrgIdsByType(orgIds, true);
        final List<String> ordinaries = getSubOrgIdsByType(orgIds, false);
        final List<String> ids = orgIds.stream().map(Organization::getId).collect(Collectors.toList());
        ids.add(id);
        if (BooleanUtils.toBoolean(org.getTenant())) {
            tenants.add(id);
        } else {
            ordinaries.add(id);
        }
        organizationMapper.updateEnabledOrganizationByIds(enabled, ids);
        prohibitOrganizationUsersByTenant(enabled, tenants);
        prohibitOrgUsersByOrdinaryOrgan(enabled, ordinaries);
    }

    @Override
    public List<String> getChildrenById(String id) {
        return getChildrenById0(id);
    }

    private List<String> getChildrenById0(String id) {
        List<Organization> orgIds = getSubOrgIds(id);
        if (CollectionUtils.isNotEmpty(orgIds)) {
            return orgIds.stream().distinct().map(Organization::getId).collect(Collectors.toList());
        }
        return Lists.newArrayList();
    }

    protected List<Organization> getSubOrgIds(String id) {
        Assert.notNull(id, "id must not be null");
        Organization organ = organizationMapper.queryOrganizationById(id);
        Assert.notNull(organ, "机构不存在！");
        String keys = organ.getParentKeys();
        if (StringUtils.isNotBlank(keys)) {
            id = keys + JOINER + id;
        }
        return organizationMapper.getSubOrganizationsById(id);
    }

    protected List<String> getSubOrgIdsByType(List<Organization> orgIds, Boolean isTenant) {
        if (CollectionUtils.isNotEmpty(orgIds)) {
            return orgIds.stream()
                    .filter(org -> BooleanUtils.toBoolean(org.getTenant()) == isTenant)
                    .map(Organization::id)
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 判断当前用户是否拥有操作权限
     *
     * @param operator 当前用户
     * @param orgId    机构ID
     */
    protected void hasOperation(LoginUser operator, String orgId) {
        String tenantId = operator.getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            Assert.isTrue(!tenantId.equals(orgId), "no permission");
        }
    }

    /**
     * 根据当前组织查询完善的parentKeys
     *
     * @param org 组织机构
     * @return java.lang.String
     */
    protected String parentKeysParameter(Organization org) {
        if (StringUtils.isNotBlank(org.getParentId())) {
            return org.getParentKeys() + JOINER + org.getId() + FUZZY;
        } else {
            return org.getId() + FUZZY;
        }
    }

    protected OrganizationEntity queryByNameAndTenantId(String organization, String tenantId) {
        List<OrganizationEntity> organizations =
                organizationMapper.selectList(new LambdaQueryWrapper<OrganizationEntity>()
                        .eq(organization != null, OrganizationEntity::getName, organization)
                        .eq(tenantId != null, OrganizationEntity::getTenantId, tenantId)
                        .isNull(tenantId == null, OrganizationEntity::getTenantId));
        return CollectionUtils.isEmpty(organizations) ? null : organizations.getFirst();
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

    /**
     * "租户"类型机构 禁用/启用组织机构下用户
     *
     * @param enabled 禁用/启用
     * @param ids     组织id
     */
    protected void prohibitOrganizationUsersByTenant(Integer enabled, List<String> ids) {
        if (CollectionUtils.isNotEmpty(ids)) {
            organizationMapper.updateEnabledRoleByOrganizationByIds(enabled, ids);
            organizationMapper.updateEnabledOrganizationUsersByTenantIds(enabled, ids);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Organization addOrganization(CreateOrganization createOrganization) {
        LoginUser operator = OperatorUtils.getOperator();
        OrganizationEntity entity = createOrganization.toEntity();
        Assert.notNull(entity.getName(), "组织机构名称不能为空");
        String tenantId = operator.getTenantId();
        OrganizationEntity checked = queryByNameAndTenantId(createOrganization.getName(), tenantId);
        Assert.isNull(checked, "组织机构名称重复！");
        String orgId;
        // 通过配置来确定组织id的来源
        if (authorityProperties.isOrganizationNameAsId()) {
            orgId = createOrganization.getName();
        } else {
            orgId = IdWorker.getIdStr();
        }
        entity.setId(orgId);
        entity.setOwner(tenantId);
        entity.setTenantId(tenantId);
        entity.setCreateDate(new Date());
        if (StringUtils.isNotBlank(createOrganization.getParentId())) {
            Organization parent = getOrganizationById(createOrganization.getParentId());
            Assert.notNull(parent, "上级组织未查询到！");
            String parentKeys = parent.buildParentKeys();
            entity.setParentKeys(parentKeys);
            entity.setLevel(TreeNodeUtils.level(parentKeys));
        } else {
            entity.setLevel(0);
            entity.setParentId(FusionConstants.TREE_TOP_LEVEL);
        }
        organizationMapper.insert(entity);
        return getOrganizationById(orgId);
    }

    @Override
    public List<String> getSubOrganizationIds(LoginUser operator) {
        List<String> orgIds = new ArrayList<>();
        if (operator instanceof UserPrincipal userPrincipal) {
            // todo 根据用户类型增加组织机构权限
            if (userPrincipal.isManager()) {
                String orgId = operator.getOrgId();
                if (StringUtils.isNotBlank(orgId)) {
                    orgIds.add(orgId);
                    orgIds.addAll(getChildrenById(orgId));
                } else {
                    orgIds.add("undefined");
                }
            }
        }
        return orgIds;
    }

    @Override
    public List<String> getSubOrganizationIds(@Nonnull String orgId) {
        List<String> list = Lists.newArrayList(orgId);
        List<String> children = getChildrenById(orgId);
        if (CollectionUtils.isNotEmpty(children)) {
            list.addAll(children);
        }
        return list;
    }

    @Override
    public void addOrganizationByImport(MultipartFile file) {
        // todo 导入
    }

    @Override
    public Organization getRootOrganizationById(String id) {
        Organization root = new Organization();
        List<String> parentKeys = getParentsById(id);
        if (CollectionUtils.isNotEmpty(parentKeys)) {
            String rootKey = parentKeys.getFirst();
            root = queryOrganizationById(rootKey);
        }
        return root;
    }

    @Override
    public Map<String, Organization> getOrganizationByIds(Set<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        List<OrganizationEntity> organizations = organizationMapper.selectByIds(ids);
        Set<String> companyIds = new HashSet<>();
        Map<String, String> orgMap = Maps.newHashMap();
        for (OrganizationEntity organization : organizations) {
            String companyId;
            if (StringUtils.isNotBlank(organization.getParentKeys())) {
                companyId = organization.getParentKeys().split(JOINER)[0];
            } else {
                companyId = organization.getId();
            }
            orgMap.put(organization.getId(), companyId);
            companyIds.add(companyId);
        }

        List<OrganizationEntity> companies = organizationMapper.selectByIds(companyIds);
        Map<String, Organization> companyMap = Maps.newHashMap();
        for (OrganizationEntity company : companies) {
            companyMap.put(company.getId(), Organization.fromEntity(Organization.class, company));
        }
        Map<String, Organization> result = Maps.newHashMap();
        for (Map.Entry<String, String> entry : orgMap.entrySet()) {
            result.put(entry.getKey(), companyMap.get(entry.getValue()));
        }
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void move(MoveResource parameter) {
        String id = parameter.getId();
        String tid = parameter.getTid();
        TreeDragMode mode = TreeDragMode.valueOf(parameter.getType());

        Organization source = organizationMapper.queryOrganizationById(id);
        Organization target = organizationMapper.queryOrganizationById(tid);
        List<Organization> changed = TreeNodeUtils.getAllChangedAfterMoved(
                source, target, mode, organizationMapper::findDirectChildren, organizationMapper::findDescendants);
        organizationMapper.updateAffectedNodesAfterMove(changed);
    }
}
