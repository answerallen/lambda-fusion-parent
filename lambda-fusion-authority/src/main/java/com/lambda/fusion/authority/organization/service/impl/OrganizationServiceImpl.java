package com.lambda.fusion.authority.organization.service.impl;

import static com.lambda.fusion.core.Constants.FUZZY;
import static com.lambda.fusion.core.Constants.JOINER;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.AuthorityProperties;
import com.lambda.fusion.authority.organization.mapper.OrganizationMapper;
import com.lambda.fusion.authority.organization.model.MutableOrganizationVO;
import com.lambda.fusion.authority.organization.model.OrganizationVO;
import com.lambda.fusion.authority.organization.model.Parameters;
import com.lambda.fusion.authority.organization.model.vo.SimpleOrgVO;
import com.lambda.fusion.authority.organization.model.UserOrganization;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.resource.model.MoveParameter;
import com.lambda.fusion.authority.role.mapper.GroupMapper;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.model.vo.MutableRoleVO;
import com.lambda.fusion.authority.role.service.RoleService;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.model.vo.MutableUserVO;
import com.lambda.fusion.core.tree.DragMode;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.fusion.core.tree.TreeUtils;
import com.lambda.fusion.core.user.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
public class OrganizationServiceImpl implements OrganizationService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RoleService roleService;
    private final OrganizationMapper organizationMapper;
    private final GroupMapper groupMapper;
    private final AuthorityProperties authorityProperties;

    @Override
    public Parameters getQueryParameter() {
        Parameters parameters = new Parameters();
        String tenantId = OperatorUtils.getOperator().getTenantId();
        parameters.setOwner(StringUtils.isNotBlank(tenantId) ? tenantId : null);
        return parameters;
    }

    @Override
    public List<OrganizationVO> treeList(Parameters parameters) {
        User operator = OperatorUtils.getLoginUser(User.class);
        List<OrganizationVO> organizations = getOrganizationsByCondition(operator, parameters);
        applyPermissionConstraints(organizations, operator);
        return TreeFactory.build(organizations);
    }

    /**
     * 根据条件获取组织列表
     *
     * @param operator 操作用户
     * @param parameters 查询参数
     * @return 组织列表
     */
    private List<OrganizationVO> getOrganizationsByCondition(User operator, Parameters parameters) {
        if (hasSearchCondition(parameters)) {
            return getOrganizationsBySearchCondition(operator, parameters);
        } else {
            return getSubordinateOrgIds(parameters);
        }
    }

    /**
     * 检查是否有搜索条件
     */
    private boolean hasSearchCondition(Parameters parameters) {
        return StringUtils.isNotBlank(parameters.getAlias()) || StringUtils.isNotBlank(parameters.getName());
    }

    /**
     * 根据搜索条件获取组织列表
     */
    private List<OrganizationVO> getOrganizationsBySearchCondition(User operator, Parameters parameters) {
        List<OrganizationVO> list = getOrgByCondition(operator, parameters);

        if (operator.isAdmin()) {
            Set<String> additionalOrgIds = collectAdditionalOrgIds(operator.getOrgId(), list);
            if (CollectionUtils.isNotEmpty(additionalOrgIds)) {
                parameters.setIds(new ArrayList<>(additionalOrgIds));
                list.addAll(organizationMapper.getAllMutableOrgan(parameters));
            }
        }

        return list.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 收集额外的组织ID（父级和子级）
     */
    private Set<String> collectAdditionalOrgIds(String orgId, List<OrganizationVO> list) {
        Set<String> orgIds = new HashSet<>();
        for (OrganizationVO org : list) {
            addParentOrgIds(orgId, org, orgIds, true);
            orgIds.addAll(getChildrenById(org.getId()));
        }
        return orgIds;
    }

    /**
     * 添加父级组织ID
     */
    private void addParentOrgIds(String orgId, OrganizationVO org, Set<String> orgIds, boolean isAdmin) {
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
    private void applyPermissionConstraints(List<OrganizationVO> organizations, User operator) {
        String operatorOrgId = operator.getOrgId();
        String operatorTenantId = operator.getTenantId();

        for (OrganizationVO org : organizations) {
            // 设置操作权限
            if (!operator.isAdmin() && org.getId().equals(operatorOrgId)) {
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
    public List<OrganizationVO> getSubordinateOrgIds(Parameters parameters) {
        LoginUser operator = OperatorUtils.getOperator();
        List<String> orgIds = getSubordinateOrgIds(operator);
        if (CollectionUtils.isNotEmpty(orgIds)) {
            parameters.setIds(orgIds);
        }
        return organizationMapper.getAllMutableOrgan(parameters);
    }

    public List<OrganizationVO> getOrgByCondition(LoginUser operator, Parameters parameters) {
        List<String> orgIds = getSubordinateOrgIds(operator);
        if (CollectionUtils.isNotEmpty(orgIds)) {
            parameters.setIds(orgIds);
        }
        return organizationMapper.getOrgIdsByCondition(parameters);
    }

    @Override
    public List<OrganizationVO> selectAll(Parameters parameters) {
        return organizationMapper.getAllMutableOrgan(parameters);
    }

    @Override
    public List<SimpleOrgVO> getSimpleOrgTree(Parameters parameters) {
        List<SimpleOrgVO> list = organizationMapper.getAllEnabledOrgan(parameters);
        return TreeFactory.build(list);
    }

    @Override
    public OrganizationVO queryOrganById(String id) {
        return getOrgById(id);
    }

    private OrganizationVO getOrgById(String id) {
        return organizationMapper.queryOrganizationById(id);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteOrganization(String id) {
        LoginUser operator = OperatorUtils.getOperator();

        // 权限检查
        validateDeletePermission(operator, id);

        // 获取组织信息
        OrganizationVO organization = getOrgById(id);
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
    private void performDeletion(OrganizationVO organization, String id) {
        if (BooleanUtils.toBoolean(organization.getTenant())) {
            deleteTenantRelatedData(id);
        }

        List<String> ids = Collections.singletonList(id);
        groupMapper.deleteByOrgIds(ids);
        organizationMapper.deleteById(id);
        organizationMapper.deleteUserOrganizationByOrg(id);
    }

    /**
     * 删除租户相关数据
     */
    private void deleteTenantRelatedData(String tenantId) {
        List<MutableRoleVO> roles = roleMapper.getTenantRolesByOwner(tenantId);
        if (CollectionUtils.isNotEmpty(roles)) {
            for (MutableRoleVO role : roles) {
                if (!BooleanUtils.toBoolean(role.getRoleType())) {
                    roleService.deleteRoleById(role.getAuthority());
                }
            }
        }

        organizationMapper.delete(new LambdaQueryWrapper<OrganizationVO>().eq(OrganizationVO::getOwner, tenantId));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public OrganizationVO updateOrganization(OrganizationVO resource) {
        LoginUser operator = OperatorUtils.getOperator();
        hasOperation(operator, resource.getId());
        Assert.notNull(resource.getId(), "机构ID不能为空");
        List<OrganizationVO> organizations = organizationMapper.queryByCondition(resource);
        Assert.isTrue(CollectionUtils.isEmpty(organizations), "lambda.authority.organ.name.repeat");
        organizationMapper.updateById(resource);
        organizationMapper.updateChildrensSpid(resource.getId(), resource.getName());
        return getOrgById(resource.getId());
    }

    @Override
    public List<MutableOrganizationVO> getAllOrganMutableUsers(List<MutableUserVO> users) {
        return organizationMapper.getAllOrganMutableUsers(users);
    }

    @Override
    public UserOrganization queryUserOrganization(UserOrganization resource) {
        Assert.notNull(resource, "organ must not be null");
        Assert.notNull(resource.getUserId(), "user id must not be null");
        return organizationMapper.queryUserOrganization(resource.getUserId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UserOrganization addUserOrganization(UserOrganization resource) {
        Assert.notNull(resource, "organ must not be null");
        Assert.notNull(resource.getUserId(), "user id must not be null");
        Assert.notNull(resource.getOrganizationId(), "organization id must not be null");
        Assert.notNull(userMapper.getMutableUserById(resource.getUserId()), "lambda.authority.organ.user.notfound");
        Assert.notNull(getOrgById(resource.getOrganizationId()), "机构不存在！");
        organizationMapper.addUserOrganization(resource);
        return resource;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteUserOrganization(String username) {
        organizationMapper.deleteUserOrganizationByUser(username);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UserOrganization updateUserOrganization(UserOrganization resource) {
        Assert.notNull(resource.getUserId(), "user id must not be null");
        Assert.notNull(resource.getOrganizationId(), "organization id must not be null");
        Assert.notNull(getOrgById(resource.getOrganizationId()), "机构不存在！");
        organizationMapper.updateUserOrganization(resource);
        return resource;
    }

    @Override
    public List<String> getParentsById(String id) {
        Assert.notNull(id, "id must not be null");
        OrganizationVO organ = organizationMapper.queryOrganizationById(id);
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
        OrganizationVO org = getOrgById(id);
        Assert.notNull(org, "org is not null");
        LoginUser operator = OperatorUtils.getOperator();
        this.hasOperation(operator, id);
        final List<OrganizationVO> orgIds = getSubOrgIds(id);
        final List<String> tenants = getSubOrgIdsByType(orgIds, true);
        final List<String> ordinaries = getSubOrgIdsByType(orgIds, false);
        final List<String> ids = orgIds.stream().map(OrganizationVO::getId).collect(Collectors.toList());
        ids.add(id);
        if (BooleanUtils.toBoolean(org.getTenant())) {
            tenants.add(id);
        } else {
            ordinaries.add(id);
        }
        organizationMapper.prohibitOrganizationByIds(enabled, ids);
        prohibitOrgUsersByTenantOrgan(enabled, tenants);
        prohibitOrgUsersByOrdinaryOrgan(enabled, ordinaries);
    }

    @Override
    public List<String> getChildrenById(String id) {
        return getChildrenById0(id);
    }

    private List<String> getChildrenById0(String id) {
        List<OrganizationVO> orgIds = getSubOrgIds(id);
        if (CollectionUtils.isNotEmpty(orgIds)) {
            return orgIds.stream().distinct().map(OrganizationVO::getId).collect(Collectors.toList());
        }
        return Lists.newArrayList();
    }

    protected List<OrganizationVO> getSubOrgIds(String id) {
        Assert.notNull(id, "id must not be null");
        OrganizationVO organ = organizationMapper.queryOrganizationById(id);
        Assert.notNull(organ, "机构不存在！");
        String keys = organ.getParentKeys();
        if (StringUtils.isNotBlank(keys)) {
            id = keys + JOINER + id;
        }
        return organizationMapper.getSubOrgIdsById(id);
    }

    protected List<String> getSubOrgIdsByType(List<OrganizationVO> orgIds, Boolean isTenant) {
        if (CollectionUtils.isNotEmpty(orgIds)) {
            return orgIds.stream()
                    .filter(org -> BooleanUtils.toBoolean(org.getTenant()) == isTenant)
                    .map(OrganizationVO::id)
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
    protected String parentKeysParameter(OrganizationVO org) {
        if (StringUtils.isNotBlank(org.getParentId())) {
            return org.getParentKeys() + JOINER + org.getId() + FUZZY;
        } else {
            return org.getId() + FUZZY;
        }
    }

    protected OrganizationVO queryByNameAndTenantId(String organization, String tenantId) {
        OrganizationVO condition = new OrganizationVO();
        condition.setName(organization);
        condition.setTenantId(tenantId);
        List<OrganizationVO> organizations = organizationMapper.queryByCondition(condition);
        return CollectionUtils.isEmpty(organizations) ? null : organizations.get(0);
    }

    /**
     * "普通"类型机构 禁用/启用组织机构下用户
     *
     * @param enabled 禁用/启用
     * @param ids     组织id
     */
    protected void prohibitOrgUsersByOrdinaryOrgan(Integer enabled, List<String> ids) {
        if (CollectionUtils.isNotEmpty(ids)) {
            organizationMapper.prohibitOrgUsersByOrdinaryOrgan(enabled, ids);
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
            organizationMapper.prohibitRoleByOrganizationByIds(enabled, ids);
            organizationMapper.prohibitOrgUsersByTenantOrgan(enabled, ids);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public OrganizationVO addOrganization(OrganizationVO resource) {
        LoginUser operator = OperatorUtils.getOperator();
        addOrgInfo(operator, resource);
        organizationMapper.addOrganization(resource);
        return resource;
    }

    /**
     * 根据当前操作人补充组织机构信息
     *
     * @param operator
     * @param resource
     * @return void
     */
    private void addOrgInfo(LoginUser operator, OrganizationVO resource) {
        Assert.notNull(resource, "organ must not be null");
        Assert.notNull(resource.getName(), "lambda.authority.organ.name.notfound");
        String tenantId = operator.getTenantId();
        OrganizationVO checked = queryByNameAndTenantId(resource.getName(), tenantId);
        Assert.isNull(checked, "lambda.authority.organ.name.repeat");
        String orgId;
        // 通过配置来确定组织id的来源
        if (authorityProperties.isOrganizationNameAsId()) {
            orgId = resource.getName();
        } else {
            orgId = IdWorker.getIdStr();
        }
        resource.setId(orgId);
        resource.setOwner(tenantId);
        resource.setTenantId(tenantId);
        resource.setCreateDate(new Date());
        if (StringUtils.isNotBlank(resource.getParentId())) {
            OrganizationVO parent = getOrgById(resource.getParentId());
            Assert.notNull(parent, "lambda.authority.organ.parent.notfound");
            String parentKeys = parent.buildParentKeys();
            resource.setParentKeys(parentKeys);
            resource.setLevel(TreeUtils.level(parentKeys));
        } else {
            resource.setLevel(0);
            resource.setParentId(TreeUtils.TOP);
        }
    }

    @Override
    public List<String> getSubordinateOrgIds(LoginUser operator) {
        List<String> orgIds = new ArrayList<>();
        //     TODO   if (!OperatorUtils.containsAnyManager(operator)) {
        String orgId = operator.getOrgId();
        if (StringUtils.isNotBlank(orgId)) {
            orgIds.add(orgId);
            orgIds.addAll(getChildrenById(orgId));
        } else {
            orgIds.add("undefined");
        }
        //        }
        return orgIds;
    }

    @Override
    public List<String> getSubordinateOrgIds(@Nonnull String orgId) {
        List<String> list = Lists.newArrayList(orgId);
        List<String> children = getChildrenById(orgId);
        if (CollectionUtils.isNotEmpty(children)) {
            list.addAll(children);
        }
        return list;
    }

    @Override
    public void addOrganizationByimport(MultipartFile file) {
        // todo 导入
    }

    private void getchildren(OrganizationVO organization, List<OrganizationVO> successList) {
        LoginUser operator = OperatorUtils.getOperator();
        for (OrganizationVO org : successList) {
            if (org.getSpid() != null && org.getSpid().equals(organization.getName())) {
                addOrgInfo(operator, org);
                org.setParentKeys(organization.buildParentKeys());
                org.setParentId(organization.getId());
                org.setLevel(organization.getLevel() + 1);
                getchildren(org, successList);
            }
        }
    }

    @Override
    public OrganizationVO getRootOrganById(String id) {
        OrganizationVO root = new OrganizationVO();
        List<String> parentKeys = getParentsById(id);
        if (CollectionUtils.isNotEmpty(parentKeys)) {
            String rootKey = parentKeys.get(0);
            root = queryOrganById(rootKey);
        }
        return root;
    }

    @Override
    public Map<String, OrganizationVO> getOrgIdsByIds(Set<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        List<OrganizationVO> organizations = organizationMapper.getOrgIdsByIds(ids);
        Set<String> companyIds = new HashSet<>();
        Map<String, String> orgMap = Maps.newHashMap();
        for (OrganizationVO organization : organizations) {
            String companyId;
            if (StringUtils.isNotBlank(organization.getParentKeys())) {
                companyId = organization.getParentKeys().split(JOINER)[0];
            } else {
                companyId = organization.getId();
            }
            orgMap.put(organization.getId(), companyId);
            companyIds.add(companyId);
        }

        List<OrganizationVO> companies = organizationMapper.getOrgIdsByIds(companyIds);
        Map<String, OrganizationVO> companyMap = Maps.newHashMap();
        for (OrganizationVO company : companies) {
            companyMap.put(company.getId(), company);
        }
        Map<String, OrganizationVO> result = Maps.newHashMap();
        for (Map.Entry<String, String> entry : orgMap.entrySet()) {
            result.put(entry.getKey(), companyMap.get(entry.getValue()));
        }
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void move(MoveParameter parameter) {
        String id = parameter.getId();
        String tid = parameter.getTid();
        DragMode mode = DragMode.valueOf(parameter.getType());

        OrganizationVO source = organizationMapper.queryOrganizationById(id);
        OrganizationVO target = organizationMapper.queryOrganizationById(tid);
        List<OrganizationVO> changed = TreeUtils.getAllChangedAfterMoved(
                source, target, mode, organizationMapper::directChildrenGetter, organizationMapper::allChildrenGetter);
        organizationMapper.batchUpdateOrgsAfterMoved(changed);
    }
}
