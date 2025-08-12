package com.lambda.fusion.authority.organization.service.impl;

import static com.lambda.fusion.core.Constants.FUZZY;
import static com.lambda.fusion.core.Constants.JOINER;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.AuthorityProperties;
import com.lambda.fusion.authority.organization.mapper.OrganizationMapper;
import com.lambda.fusion.authority.organization.model.MutableOrganization;
import com.lambda.fusion.authority.organization.model.Organization;
import com.lambda.fusion.authority.organization.model.Parameters;
import com.lambda.fusion.authority.organization.model.SimpleOrg;
import com.lambda.fusion.authority.organization.model.UserOrganization;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.resource.model.MoveParameter;
import com.lambda.fusion.authority.role.mapper.GroupMapper;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.model.MutableRole;
import com.lambda.fusion.authority.role.service.RoleService;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.model.MutableUser;
import com.lambda.fusion.core.tree.DragMode;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.fusion.core.tree.TreeUtils;
import com.lambda.fusion.core.user.User;
import jakarta.annotation.Resource;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
public class OrganizationServiceImpl implements OrganizationService {

    @Resource
    protected UserMapper userMapper;

    @Resource
    protected RoleMapper roleMapper;

    @Autowired
    protected RoleService roleService;

    @Resource
    protected OrganizationMapper organizationMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private AuthorityProperties authorityProperties;

    @Override
    public Parameters getQueryParameter() {
        Parameters parameters = new Parameters();
        String tenantId = OperatorUtils.getOperator().getTenantId();
        parameters.setOwner(StringUtils.isNotBlank(tenantId) ? tenantId : null);
        return parameters;
    }

    @Override
    public List<Organization> treeList(Parameters parameters) {
        User operator = OperatorUtils.getLoginUser(User.class);
        String orgId = operator.getOrgId();
        List<Organization> list;
        HashSet<String> orgIds = new HashSet<>();
        if (StringUtils.isNotBlank(parameters.getAlias()) || StringUtils.isNotBlank(parameters.getName())) {
            list = getOrgByCondition(operator, parameters);
            if (operator.isAdmin()) {
                getParentAndChildrenIds(orgId, list, orgIds, true);
                if (CollectionUtils.isNotEmpty(orgIds)) {
                    parameters.setIds(new ArrayList<>(orgIds));
                    list.addAll(organizationMapper.getAllMutableOrgan(parameters));
                }
            }
            list = list.stream().distinct().collect(Collectors.toList());
        } else {
            list = getSubordinateOrgIds(parameters);
        }
        for (Organization org : list) {
            if (!operator.isAdmin() && org.getId().equals(orgId)) {
                org.setNoPermission(true);
            }
            if (!Objects.equals(operator.getTenantId(), org.getOwner())) {
                org.setNoPermission(true);
                org.setInAvailable(true);
            }
        }
        return TreeFactory.build(list);
    }

    private void getParentAndChildrenIds(
            String orgId, List<Organization> list, HashSet<String> orgIds, boolean isOrgAdmin) {
        for (Organization org : list) {
            String parentKeys = org.getParentKeys();
            if (StringUtils.isNotBlank(parentKeys)) {
                String[] split = StringUtils.split(parentKeys, JOINER);
                List<String> ids = new ArrayList<>(Arrays.asList(split));
                if (isOrgAdmin) {
                    String substring = StringUtils.substring(parentKeys, StringUtils.indexOf(parentKeys, orgId));
                    split = StringUtils.split(substring, JOINER);
                    ids = new ArrayList<>(Arrays.asList(split));
                }
                orgIds.addAll(ids);
            }
            orgIds.addAll(getChildrenById(org.getId()));
        }
    }

    @Override
    public List<Organization> getSubordinateOrgIds(Parameters parameters) {
        LoginUser operator = OperatorUtils.getOperator();
        List<String> orgIds = getSubordinateOrgIds(operator);
        if (CollectionUtils.isNotEmpty(orgIds)) {
            parameters.setIds(orgIds);
        }
        return organizationMapper.getAllMutableOrgan(parameters);
    }

    public List<Organization> getOrgByCondition(LoginUser operator, Parameters parameters) {
        List<String> orgIds = getSubordinateOrgIds(operator);
        if (CollectionUtils.isNotEmpty(orgIds)) {
            parameters.setIds(orgIds);
        }
        return organizationMapper.getOrgIdsByCondition(parameters);
    }

    @Override
    public List<Organization> selectAll(Parameters parameters) {
        return organizationMapper.getAllMutableOrgan(parameters);
    }

    @Override
    public List<SimpleOrg> getSimpleOrgTree(Parameters parameters) {
        List<SimpleOrg> list = organizationMapper.getAllEnabledOrgan(parameters);
        return TreeFactory.build(list);
    }

    @Override
    public Organization queryOrganById(String id) {
        return getOrgById(id);
    }

    private Organization getOrgById(String id) {
        return organizationMapper.queryOrganizationById(id);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteOrganization(String id) {
        LoginUser operator = OperatorUtils.getOperator();
        this.hasOperation(operator, id);
        Organization organization = getOrgById(id);
        Assert.notNull(organization, "组织不存在！");
        List<String> ids = getChildrenById0(id);
        Assert.state(CollectionUtils.isEmpty(ids), "");
        ids.add(id);
        boolean hasUser = organizationMapper.existUser(ids);
        Assert.state(!hasUser, "user not empty");
        if (BooleanUtils.toBoolean(organization.getTenant())) {
            List<MutableRole> roles = roleMapper.getTenantRolesByOwner(id);
            if (CollectionUtils.isNotEmpty(roles)) {
                for (MutableRole role : roles) {
                    if (!BooleanUtils.toBoolean(role.getRoleType())) {
                        roleService.deleteRoleById(role.getAuthority());
                    }
                }
            }
            organizationMapper.deleteTenantOrganizationRole(id);
        }
        groupMapper.deleteByOrgIds(ids);
        organizationMapper.deleteOrganizationByPk(id);
        organizationMapper.deleteUserOrganizationByOrg(id);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Organization updateOrganization(Organization resource) {
        LoginUser operator = OperatorUtils.getOperator();
        hasOperation(operator, resource.getId());
        Assert.notNull(resource.getId(), "机构ID不能为空");
        List<Organization> organizations = organizationMapper.queryByCondition(resource);
        Assert.isTrue(CollectionUtils.isEmpty(organizations), "lambda.authority.organ.name.repeat");
        organizationMapper.updateOrganizationByPk(resource);
        organizationMapper.updateChildrensSpid(resource.getId(), resource.getName());
        return getOrgById(resource.getId());
    }

    @Override
    public List<MutableOrganization> getAllOrganMutableUsers(List<MutableUser> users) {
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
        Organization org = getOrgById(id);
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
        organizationMapper.prohibitOrganizationByIds(enabled, ids);
        prohibitOrgUsersByTenantOrgan(enabled, tenants);
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
        return organizationMapper.getSubOrgIdsById(id);
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

    protected Organization queryByNameAndTenantId(String organization, String tenantId) {
        Organization condition = new Organization();
        condition.setName(organization);
        condition.setTenantId(tenantId);
        List<Organization> organizations = organizationMapper.queryByCondition(condition);
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
    public Organization addOrganization(Organization resource) {
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
    private void addOrgInfo(LoginUser operator, Organization resource) {
        Assert.notNull(resource, "organ must not be null");
        Assert.notNull(resource.getName(), "lambda.authority.organ.name.notfound");
        String tenantId = operator.getTenantId();
        Organization checked = queryByNameAndTenantId(resource.getName(), tenantId);
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
            Organization parent = getOrgById(resource.getParentId());
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

    private void getchildren(Organization organization, List<Organization> successList) {
        LoginUser operator = OperatorUtils.getOperator();
        for (Organization org : successList) {
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
    public Organization getRootOrganById(String id) {
        Organization root = new Organization();
        List<String> parentKeys = getParentsById(id);
        if (CollectionUtils.isNotEmpty(parentKeys)) {
            String rootKey = parentKeys.get(0);
            root = queryOrganById(rootKey);
        }
        return root;
    }

    @Override
    public Map<String, Organization> getOrgIdsByIds(Set<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyMap();
        }
        List<Organization> organizations = organizationMapper.getOrgIdsByIds(ids);
        Set<String> companyIds = new HashSet<>();
        Map<String, String> orgMap = Maps.newHashMap();
        for (Organization organization : organizations) {
            String companyId;
            if (StringUtils.isNotBlank(organization.getParentKeys())) {
                companyId = organization.getParentKeys().split(JOINER)[0];
            } else {
                companyId = organization.getId();
            }
            orgMap.put(organization.getId(), companyId);
            companyIds.add(companyId);
        }

        List<Organization> companies = organizationMapper.getOrgIdsByIds(companyIds);
        Map<String, Organization> companyMap = Maps.newHashMap();
        for (Organization company : companies) {
            companyMap.put(company.getId(), company);
        }
        Map<String, Organization> result = Maps.newHashMap();
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

        Organization source = organizationMapper.queryOrganizationById(id);
        Organization target = organizationMapper.queryOrganizationById(tid);
        List<Organization> changed = TreeUtils.getAllChangedAfterMoved(
                source, target, mode, organizationMapper::directChildrenGetter, organizationMapper::allChildrenGetter);
        organizationMapper.batchUpdateOrgsAfterMoved(changed);
    }
}
