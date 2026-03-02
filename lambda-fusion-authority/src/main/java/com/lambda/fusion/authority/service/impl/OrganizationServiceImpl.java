package com.lambda.fusion.authority.service.impl;

import static com.lambda.fusion.core.FusionConstants.FUZZY;
import static com.lambda.fusion.core.FusionConstants.JOINER;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.AuthorityProperties;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.mapper.GroupMapper;
import com.lambda.fusion.authority.mapper.OrganizationMapper;
import com.lambda.fusion.authority.mapper.RoleMapper;
import com.lambda.fusion.authority.mapper.UserMapper;
import com.lambda.fusion.authority.mapper.UserOrganizationMapper;
import com.lambda.fusion.authority.model.organization.*;
import com.lambda.fusion.authority.model.resource.MoveResource;
import com.lambda.fusion.authority.model.role.Role;
import com.lambda.fusion.authority.model.user.User;
import com.lambda.fusion.authority.service.OrganizationService;
import com.lambda.fusion.authority.service.RoleService;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.LoginUserDetails;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.tree.model.TreeDragMode;
import com.lambda.fusion.core.tree.util.TreeNodeUtils;
import com.lambda.fusion.core.utils.SecurityUtils;
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
        LoginUserDetails loginUserDetails = SecurityUtils.getUser();
        List<Organization> organizations = queryOrganizations(loginUserDetails, organizationQuery);
        this.applyPermissionConstraints(organizations, loginUserDetails);
        return TreeBuilder.build(organizations);
    }

    /**
     * 根据条件获取组织列表
     *
     * @param loginUserDetails 操作用户
     * @param organizationQuery    查询参数
     * @return 组织列表
     */
    private List<Organization> queryOrganizations(
            LoginUserDetails loginUserDetails, OrganizationQuery organizationQuery) {
        if (StringUtils.isNotBlank(organizationQuery.getAlias())
                || StringUtils.isNotBlank(organizationQuery.getName())) {
            return getOrganizations(loginUserDetails, organizationQuery);
        } else {
            return getSubOrganizations(organizationQuery);
        }
    }

    /**
     * 根据搜索条件获取组织列表
     */
    private List<Organization> getOrganizations(LoginUserDetails principal, OrganizationQuery organizationQuery) {
        List<Organization> list = getOrgByCondition(principal, organizationQuery);
        if (principal.isAdmin()) {
            Set<String> additionalOrgIds = collectAdditionalOrgIds(principal.getOrgId(), list);
            if (CollectionUtils.isNotEmpty(additionalOrgIds)) {
                organizationQuery.setIds(new ArrayList<>(additionalOrgIds));
                list.addAll(organizationMapper.selectOrganizations(organizationQuery));
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
            this.addParentOrgIds(orgId, org, orgIds, SecurityUtils.getUser().isManager());
            orgIds.addAll(getChildrenById(org.getId()));
        }
        return orgIds;
    }

    /**
     * 添加 父级组织 ID
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
    private void applyPermissionConstraints(List<Organization> organizations, LoginUserDetails loginUserDetails) {
        String operatorOrgId = loginUserDetails.getOrgId();
        String operatorTenantId = loginUserDetails.getTenantId();
        for (Organization organization : organizations) {
            // 设置操作权限
            if (!loginUserDetails.isAdmin() && organization.getId().equals(operatorOrgId)) {
                organization.setHasPermission(true);
            }

            // 设置租户权限
            if (!Objects.equals(operatorTenantId, organization.getOwner())) {
                organization.setHasPermission(true);
                organization.setSelectable(true);
            }
        }
    }

    @Override
    public List<Organization> getSubOrganizations(OrganizationQuery organizationQuery) {
        List<String> orgIds = getSubOrganizations(SecurityUtils.getUser());
        if (CollectionUtils.isNotEmpty(orgIds)) {
            organizationQuery.setIds(orgIds);
        }
        return organizationMapper.selectOrganizations(organizationQuery);
    }

    public List<Organization> getOrgByCondition(LoginUser operator, OrganizationQuery organizationQuery) {
        List<String> orgIds = getSubOrganizations(operator);
        if (CollectionUtils.isNotEmpty(orgIds)) {
            organizationQuery.setIds(orgIds);
        }
        return organizationMapper.selectOrganizationsByQuery(organizationQuery);
    }

    @Override
    public List<Organization> selectAll(OrganizationQuery parameters) {
        return organizationMapper.selectOrganizations(parameters);
    }

    @Override
    public List<OrganizationTree> getOrganizationTree(OrganizationQuery organizationQuery) {
        List<OrganizationTree> list = organizationMapper.selectEnabledOrganization(organizationQuery);
        return TreeBuilder.build(list);
    }

    @Override
    public Organization queryOrganizationById(String id) {
        return getOrganizationById(id);
    }

    private Organization getOrganizationById(String id) {
        return organizationMapper.selectOrganizationById(id);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteOrganization(String id) {
        LoginUser operator = OperatorUtils.getOperator();

        // 权限检查
        validateDeletePermission(operator, id);

        // 获取组织信息
        Organization organization = getOrganizationById(id);
        if (organization == null) {
            throw AuthorityBusinessException.organizationNotFound(id);
        }

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
        if (CollectionUtils.isNotEmpty(childIds)) {
            throw AuthorityBusinessException.organizationHasChildren(id);
        }

        List<String> allIds = new ArrayList<>(childIds);
        allIds.add(id);

        boolean hasUser = organizationMapper.existUser(allIds);
        if (hasUser) {
            throw AuthorityBusinessException.operationNotSupported("组织下存在用户，无法删除");
        }
    }

    /**
     * 执行删除操作
     */
    private void performDeletion(Organization organization, String id) {
        if (BooleanUtils.toBoolean(organization.getCategory())) {
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
        if (resource.getId() == null) {
            throw AuthorityBusinessException.invalidParameter("机构ID不能为空");
        }
        List<OrganizationEntity> organizations =
                organizationMapper.selectList(new LambdaQueryWrapper<OrganizationEntity>()
                        .eq(OrganizationEntity::getName, resource.getName())
                        .eq(OrganizationEntity::getOwner, operator.getTenantId()));
        if (CollectionUtils.isNotEmpty(organizations)) {
            throw AuthorityBusinessException.organizationNameExists(resource.getName());
        }
        OrganizationEntity organizationEntity = resource.toEntity();
        organizationMapper.updateById(organizationEntity);
        return getOrganizationById(resource.getId());
    }

    @Override
    public List<OrganizationWithUser> getAllOrganMutableUsers(List<User> users) {
        return organizationMapper.selectOrganizationByUsers(users);
    }

    @Override
    public UserOrganization queryUserOrganization(UserOrganizationChange resource) {
        if (resource == null) {
            throw AuthorityBusinessException.invalidParameter("参数不能为空");
        }
        if (resource.getUsername() == null) {
            throw AuthorityBusinessException.invalidParameter("用户ID不能为空");
        }
        UserOrganizationEntity userOrganizationEntity =
                userOrganizationMapper.selectUserOrganization(resource.getUsername());
        return UserOrganization.fromEntity(UserOrganization.class, userOrganizationEntity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UserOrganization addUserOrganization(UserOrganizationChange userOrganizationDTO) {
        if (userOrganizationDTO == null) {
            throw AuthorityBusinessException.invalidParameter("参数不能为空");
        }
        if (userOrganizationDTO.getUsername() == null) {
            throw AuthorityBusinessException.invalidParameter("用户ID不能为空");
        }
        if (userOrganizationDTO.getOrganizationId() == null) {
            throw AuthorityBusinessException.invalidParameter("组织ID不能为空");
        }
        User user = userMapper.selectUserByUsername(userOrganizationDTO.getUsername());
        if (user == null) {
            throw AuthorityBusinessException.userNotFound(userOrganizationDTO.getUsername());
        }
        Organization organization = getOrganizationById(userOrganizationDTO.getOrganizationId());
        if (organization == null) {
            throw AuthorityBusinessException.organizationNotFound(userOrganizationDTO.getOrganizationId());
        }
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
        if (resource.getUsername() == null) {
            throw AuthorityBusinessException.invalidParameter("用户ID不能为空");
        }
        if (resource.getOrganizationId() == null) {
            throw AuthorityBusinessException.invalidParameter("组织ID不能为空");
        }
        Organization organization = getOrganizationById(resource.getOrganizationId());
        if (organization == null) {
            throw AuthorityBusinessException.organizationNotFound(resource.getOrganizationId());
        }
        UserOrganizationEntity userOrganization = resource.toEntity();
        userOrganizationMapper.updateById(userOrganization);
        return UserOrganization.fromEntity(UserOrganization.class, userOrganization);
    }

    @Override
    public List<String> getParentsById(String id) {
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("ID不能为空");
        }
        Organization organ = organizationMapper.selectOrganizationById(id);
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
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("ID不能为空");
        }
        Organization org = getOrganizationById(id);
        if (org == null) {
            throw AuthorityBusinessException.organizationNotFound(id);
        }
        LoginUser operator = OperatorUtils.getOperator();
        this.hasOperation(operator, id);
        final List<Organization> orgIds = getSubOrgIds(id);
        final List<String> tenants = getSubOrgIdsByType(orgIds, true);
        final List<String> ordinaries = getSubOrgIdsByType(orgIds, false);
        final List<String> ids = orgIds.stream().map(Organization::getId).collect(Collectors.toList());
        ids.add(id);
        if (BooleanUtils.toBoolean(org.getCategory())) {
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
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("ID不能为空");
        }
        Organization organ = organizationMapper.selectOrganizationById(id);
        if (organ == null) {
            throw AuthorityBusinessException.organizationNotFound(id);
        }
        String keys = organ.getParentKeys();
        if (StringUtils.isNotBlank(keys)) {
            id = keys + JOINER + id;
        }
        return organizationMapper.selectSubOrganizationsById(id);
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

    /**
     * 判断当前用户是否拥有操作权限
     *
     * @param operator 当前用户
     * @param orgId    机构ID
     */
    protected void hasOperation(LoginUser operator, String orgId) {
        String tenantId = operator.getTenantId();
        if (StringUtils.isNotBlank(tenantId) && tenantId.equals(orgId)) {
            throw AuthorityBusinessException.authNoPermission();
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
        if (entity.getName() == null) {
            throw AuthorityBusinessException.invalidParameter("组织机构名称不能为空");
        }
        String tenantId = operator.getTenantId();
        OrganizationEntity checked = queryByNameAndTenantId(createOrganization.getName(), tenantId);
        if (checked != null) {
            throw AuthorityBusinessException.organizationNameExists(createOrganization.getName());
        }
        String orgId;
        // 通过配置来确定组织id的来源
        if (authorityProperties.isUseOrgNameAsId()) {
            orgId = createOrganization.getName();
        } else {
            orgId = IdWorker.getIdStr();
        }
        entity.setId(orgId);
        entity.setOwner(tenantId);
        entity.setTenantId(tenantId);
        entity.setCreatedAt(new Date());
        if (StringUtils.isNotBlank(createOrganization.getParentId())) {
            Organization parent = getOrganizationById(createOrganization.getParentId());
            if (parent == null) {
                throw AuthorityBusinessException.organizationNotFound(createOrganization.getParentId());
            }
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
    public List<String> getSubOrganizations(LoginUser operator) {
        List<String> orgIds = new ArrayList<>();
        if (operator instanceof LoginUserDetails loginUserDetails) {
            // 根据用户类型增加组织机构权限
            if (!loginUserDetails.isManager()) {
                String orgId = operator.getOrgId();
                if (StringUtils.isNotBlank(orgId)) {
                    orgIds.add(orgId);
                    List<String> children = getChildrenById(orgId);
                    orgIds.addAll(children);
                } else {
                    orgIds.add("undefined");
                }
            }
        }
        return orgIds;
    }

    @Override
    public List<String> getSubOrganizations(@Nonnull String orgId) {
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
    public Map<String, Organization> getOrganizationMapByIds(Set<String> ids) {
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

        List<OrganizationEntity> organizationEntityList = organizationMapper.selectByIds(companyIds);
        Map<String, Organization> organizationMap = Maps.newHashMap();
        for (OrganizationEntity company : organizationEntityList) {
            organizationMap.put(company.getId(), Organization.fromEntity(Organization.class, company));
        }
        Map<String, Organization> result = Maps.newHashMap();
        for (Map.Entry<String, String> entry : orgMap.entrySet()) {
            result.put(entry.getKey(), organizationMap.get(entry.getValue()));
        }
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void move(MoveResource parameter) {
        String id = parameter.getId();
        String tid = parameter.getTid();
        TreeDragMode mode = TreeDragMode.valueOf(parameter.getType());

        Organization source = organizationMapper.selectOrganizationById(id);
        Organization target = organizationMapper.selectOrganizationById(tid);
        List<Organization> changed = TreeNodeUtils.getAllChangedAfterMoved(
                source,
                target,
                mode,
                organizationMapper::selectChildren,
                organizationMapper::selectOrganizationsByParentKeys);
        organizationMapper.updateAffectedNodesAfterMove(changed);
    }
}
