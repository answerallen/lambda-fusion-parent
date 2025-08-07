package com.lambda.fusion.auth.role.service;

import static com.lambda.fusion.autoconfig.AuthorizeConstants.CACHE_MANAGER;
import static com.lambda.fusion.autoconfig.AuthorizeConstants.DEFAULT_GROUP_NAME;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.auth.resource.model.MutableResource;
import com.lambda.fusion.auth.resource.model.ResourceType;
import com.lambda.fusion.auth.resource.service.ResourceService;
import com.lambda.fusion.auth.role.bean.*;
import com.lambda.fusion.auth.role.mapper.AccessPermissionMapper;
import com.lambda.fusion.auth.role.mapper.GroupMapper;
import com.lambda.fusion.auth.role.mapper.RoleMapper;
import com.lambda.fusion.auth.role.mapper.UserRolesMapper;
import com.lambda.fusion.auth.tenant.service.TenantAuthorizeManager;
import com.lambda.fusion.autoconfig.AuthorizeConstants;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.tree.TreeFactory;
import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class RoleServiceImpl implements RoleService {

    private static final String[] BUILT_IN_ROLES = {
        "ROLE_SYSTEM", "ROLE_ADMIN", "ROLE_DEV", "ROLE_USER", "ROLE_MANAGER", "ROLE_ORG"
    };
    public static final String DEFAULT = "default";

    private static final String ADMIN = "admin";

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private UserRolesMapper userRolesMapper;

    @Resource
    private AccessPermissionMapper accessPermissionMapper;

    @Autowired
    private InternalRoleService internalRoleService;

    @Autowired(required = false)
    private TenantAuthorizeManager tenantAuthorizeManager;

    @Override
    public List<MutableRole> getAllRoles(LoginUser operator) {
        //        boolean dev = OperatorUtils.isDev(operator);
        //        boolean admin = OperatorUtils.isAdmin(operator);
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(4);
        //        parameters.put("dev", dev);
        //        parameters.put(ADMIN, admin);
        parameters.put("userid", operator.getUsername());
        parameters.put(Constants.TENANT_ID, operator.getTenantId());
        return roleMapper.getAllRoles(parameters);
    }

    @SuppressWarnings("squid:S3776")
    @Override
    public List<GroupRoleVo> getAllGroupRoles(LoginUser operator, String tenantId) {
        if (StringUtils.isBlank(tenantId) || StringUtils.isNotBlank(operator.getTenantId())) {
            tenantId = operator.getTenantId();
        }
        Set<String> excludes = Sets.newHashSet();
        excludes.add(Constants.ROLE_USER);
        excludes.add(Constants.ROLE_HMAC);
        excludes.add(Constants.ROLE_SYSTEM);
        excludes.add(Constants.ROLE_TENANT);
        //        if (!OperatorUtils.isDev(operator)) {
        //            excludes.add(Constants.ROLE_DEV);
        //            if (!OperatorUtils.isAdmin(operator)) {
        //                excludes.add(Constants.ROLE_ADMIN);
        //            }
        //        }
        Set<String> queryExclude = internalRoleService.queryExclude(operator);
        excludes.addAll(queryExclude);
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(2);
        parameters.put("excludes", excludes);
        parameters.put(Constants.TENANT_ID, tenantId);
        List<MutableRole> roles = roleMapper.getAllRoles(parameters);
        List<Group> groups = groupMapper.getAllGroup(parameters);
        Group defaultGroup = newDefaultGroup(tenantId);
        if (notcontains(groups, defaultGroup)) {
            groups.add(defaultGroup);
        }
        // Jin 如果当前用户的角色是开发工程师 只返回ROLE_DEV和ROLE_ADMIN
        final Map<String, List<MutableRole>> map = roles.stream()
                .filter(mutableRole -> {
                    //                    if (OperatorUtils.isDev(operator)) {
                    //                        return (mutableRole.getAuthority().equals(Constants.ROLE_DEV) ||
                    // mutableRole.getAuthority().equals(Constants.ROLE_ADMIN));
                    //                    } else {
                    return true;
                    //                    }
                })
                .collect(Collectors.groupingBy(MutableRole::getGroupId));

        List<GroupRoleVo> result = new ArrayList<>();
        groups.forEach(item -> {
            GroupRoleVo group = BeanUtil.copyProperties(item, GroupRoleVo.class);
            List<MutableRole> children = map.get(group.getGroupId());
            if (CollectionUtils.isNotEmpty(children)) {
                group.setRoles(children);
                group.setInAvailable(true);
                group.setNoPermission(false);
            }
            result.add(group);
        });
        return result;
    }

    @Override
    public Page<MutableRole> getAllRoles(Page<MutableRole> pageable, Map<String, Object> parameters) {
        pageable = roleMapper.getAllMutableRoles(pageable, parameters);
        List<MutableRole> roles = pageable.getRecords();
        if (CollectionUtils.isNotEmpty(roles)) {
            for (MutableRole item : roles) {
                String authority = item.getAuthority();
                item.setBuiltIn(ArrayUtils.contains(BUILT_IN_ROLES, authority));
            }
        }
        return pageable;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MutableRole updateRole(LoginUser operator, MutableRole role) {
        Assert.notNull(role, AuthorizeConstants.ROLE_NOT_FOUND);
        Assert.notNull(role.getAlias(), "lambda.authority.role.alias.notempty");
        MutableRole source = getRoleByAuthority(role.getAuthority());
        source.setAlias(role.getAlias());
        source.setRemarks(role.getRemarks());
        source.setIcon(role.getIcon());
        source.setDataType(role.getDataType());
        source.setRoleType(role.getRoleType());
        source.setGroupId(role.getGroupId());
        roleMapper.updateRole(source);
        source = getRoleByAuthority(role.getAuthority());
        return source;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MutableRole saveRole(LoginUser operator, MutableRole role) {
        Assert.notNull(role, AuthorizeConstants.ROLE_NOT_FOUND);
        Assert.notNull(role.getAlias(), "别名不能为空！");
        String tenantId = operator.getTenantId();
        String authority = UUID.fastUUID().toString();
        Assert.isTrue(!roleMapper.hasExists(authority), "角色" + authority + "已存在");
        role.setAuthority(authority);
        role.setCreateDate(new Date());
        role.setAuthority(authority);
        role.setOwner(tenantId);
        role.setTenantId(tenantId);
        String groupId = Optional.ofNullable(role.getGroupId()).orElse(DEFAULT);
        role.setGroupId(groupId);
        roleMapper.insertRole(role);
        return getRoleByAuthority(authority);
    }

    @Override
    public MutableRole getRoleByAuthority(String authority) {
        Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
        if (authority.startsWith(Constants.ROLE_TENANT)) {
            authority = Constants.ROLE_TENANT;
        }
        return roleMapper.getRoleByAuthority(authority);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoleById(String authority) {
        Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
        Set<String> excludes = Stream.of(BUILT_IN_ROLES).collect(Collectors.toSet());
        Set<String> deleteExclude = internalRoleService.deleteExclude(OperatorUtils.getOperator());
        excludes.addAll(deleteExclude);
        Assert.isTrue(!excludes.contains(authority), "角色" + authority + "不能删除");
        MutableRole role = getRoleByAuthority(authority);
        authority = role.getAuthority();
        Assert.notNull(role, "角色" + authority + "不存在");
        boolean hasUsedAuthority = hasUsedAuthority(authority);
        Assert.isTrue(!hasUsedAuthority, "lambda.authority.role.delete.used");
        roleMapper.deleteResourceRoleByAuthority(authority);
        roleMapper.deleteRoleByAuthority(authority);
        roleMapper.deleteUserRoleByAuthority(authority);
    }

    @Override
    public boolean hasExists(String authority) {
        Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
        return roleMapper.hasExists(authority);
    }

    @Override
    public List<AccessPermission> getAccessPermissions(LoginUser operator, String authority, Integer mode) {
        Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
        //        boolean dev = OperatorUtils.isDev(operator);
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(3);
        parameters.put("authority", authority);
        parameters.put("mode", Optional.ofNullable(mode).orElse(0));
        ////        if (!dev) {
        //            boolean self = OperatorUtils.hasAuthorize(operator, authority);
        //            Assert.isTrue(!self, AuthorizeConstants.ROLE_SELF_REFUSED);
        //            Set<String> authorities = operator.getAuthorities().stream()
        //                    .map(SimpleGrantedAuthority::getAuthority).collect(Collectors.toSet());
        //            authorities.add(operator.getUsername());
        //            parameters.put("authorities", authorities);
        //        }
        List<AccessPermission> permissions = roleMapper.getAccessPermissions(parameters);
        if (CollectionUtils.isEmpty(permissions)) {
            return Collections.emptyList();
        }
        //        NavigationUtils.migratePermissions(permissions);
        return TreeFactory.build(permissions);
    }

    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAuthorization(String authority, String resourceid, int status, LoginUser operator) {
        Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
        Assert.notNull(resourceid, "lambda.authority.role.resource.notempty");
        //        boolean dev = OperatorUtils.isDev(operator);
        //        if (!dev) {
        //            boolean self = OperatorUtils.hasAuthorize(operator, authority);
        //            Assert.isTrue(!self, AuthorizeConstants.ROLE_SELF_REFUSED);
        //        }
        String tenantId = operator.getTenantId();
        //        if (StringUtils.isBlank(tenantId)) {
        //            tenantId = RoleUtil.getTenantId(authority);
        //        }
        MutableResource resource = resourceService.getResourceById(resourceid);
        if (null != resource) {
            List<MutableResource> resources = resourceService.getAllParentsByOperator(operator, resource);
            List<MutableResource> children = resourceService.getAllChildrenByOperator(operator, resource);
            resources.add(resource);
            resources.addAll(children);
            Set<String> ids = resources.stream().map(MutableResource::getId).collect(Collectors.toSet());
            Set<String> authorized = Sets.newHashSet(roleMapper.hasAuthorizedWithIntersection(authority, ids));

            Set<String> intersection = Sets.intersection(ids, authorized);
            if (CollectionUtils.isNotEmpty(intersection)) {
                AccessPermissionDO parameters = new AccessPermissionDO();
                parameters.setAuthority(authority);
                parameters.setIds(intersection);
                parameters.setTenantId(tenantId);
                parameters.setStatus(status);
                roleMapper.batchUpdateAuthorization(parameters);
            }
            Set<String> differently = Sets.difference(ids, authorized);
            if (!CollectionUtils.isEmpty(differently)) {
                String finalTenantId = tenantId;
                //                MybatisUtils.batchInsert(differently, RoleMapper.class, (id, mapper) -> {
                //                    AccessPermissionDO parameters = new AccessPermissionDO();
                //                    parameters.setAuthority(authority);
                //                    parameters.setTenantId(finalTenantId);
                //                    parameters.setStatus(status);
                //                    parameters.setId(id);
                //                    mapper.saveAuthorization(parameters);
                //                });
            }

            // 处理租户主库
            if (tenantAuthorizeManager != null) {
                tenantAuthorizeManager.saveAuth(authority, resources, status);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    public void deleteAuthorization(String authority, String resourceid, LoginUser operator) {
        Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
        Assert.notNull(resourceid, "lambda.authority.role.resource.notempty");
        //        boolean dev = OperatorUtils.isDev(operator);
        //        if (!dev) {
        //            boolean self = OperatorUtils.hasAuthorize(operator, authority);
        //            Assert.isTrue(!self, AuthorizeConstants.ROLE_SELF_REFUSED);
        //        }
        String tenantId = operator.getTenantId();
        //        if (StringUtils.isBlank(tenantId)) {
        //            tenantId = RoleUtil.getTenantId(authority);
        //        }
        MutableResource resource = resourceService.getResourceById(resourceid);
        if (null != resource) {
            List<MutableResource> resources = resourceService.getAllChildrenByOperator(operator, resource);
            resources.add(resource);
            List<String> ids = resources.stream().map(MutableResource::getId).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(ids)) {
                roleMapper.batchDeleteAuthorization(authority, ids, tenantId);
            }
            if (!ResourceType.BUTTON.equals(ResourceType.get(resource.getResType()))) {
                List<MutableResource> parents = resourceService.getAllParentsByOperator(operator, resource);
                for (MutableResource parent : parents) {
                    AccessPermissionDO parameters = new AccessPermissionDO();
                    parameters.setId(parent.getId());
                    parameters.setTenantId(tenantId);
                    parameters.setAuthority(authority);
                    boolean state = accessPermissionMapper.noAnyChildrenPermission(parameters);
                    if (state) {
                        accessPermissionMapper.deletePermission(parameters);
                    }
                }
            }

            // 处理租户主库
            if (tenantAuthorizeManager != null) {
                tenantAuthorizeManager.deleteAuthorization(authority, resources);
            }
        }
    }

    @Override
    public boolean hasUsedAuthority(String authority) {
        return roleMapper.hasUsedAuthority(authority);
    }

    @Override
    public void prohibitRole(int type, String authority) {
        Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
        MutableRole role = getRoleByAuthority(authority);
        authority = role.getAuthority();
        Assert.notNull(role, AuthorizeConstants.ROLE_NOT_FOUND);
        roleMapper.prohibitRole(type, authority);
    }

    @Override
    public List<MutableRole> getTenantRolesByOwner(String owner) {
        return roleMapper.getTenantRolesByOwner(owner);
    }

    @Override
    public GroupVo addGroup(GroupVo groupVo) {
        groupVo.setGroupId(IdWorker.getIdStr());
        groupMapper.insert(BeanUtil.copyProperties(groupVo, Group.class));
        return groupVo;
    }

    @Override
    public void deleteGroup(String groupId) {
        Assert.isFalse(Objects.equals(groupId, DEFAULT), AuthorizeConstants.ROLE_GROUP_ILLEGAL_OPERATION_DEL_DEFAULT);
        groupMapper.updateRoleGroupId(groupId, DEFAULT);
        groupMapper.deleteById(groupId);
    }

    @Override
    public GroupVo updateGroup(GroupVo groupVo) {
        groupMapper.updateById(BeanUtil.copyProperties(groupVo, Group.class));
        return groupVo;
    }

    @Override
    public GroupVo getGroupById(String id) {
        Group group = groupMapper.selectById(id);
        if (group != null) {
            GroupVo target = new GroupVo();
            BeanUtil.copyProperties(group, target);
            return target;
        }
        return null;
    }

    @Override
    public List<GroupVo> listGroups(LoginUser operator) {
        String tenantId = operator.getTenantId();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(1);
        parameters.put(Constants.TENANT_ID, tenantId);
        Group defaultGroup = newDefaultGroup(tenantId);
        List<Group> groups = groupMapper.getAllGroup(parameters);
        if (notcontains(groups, defaultGroup)) {
            groups.add(defaultGroup);
        }
        return BeanUtil.copyToList(groups, GroupVo.class);
    }

    private boolean notcontains(List<Group> groups, Group defaultGroup) {
        return CollectionUtils.isEmpty(groups)
                || (CollectionUtils.isNotEmpty(groups) && !groups.contains(defaultGroup));
    }

    private Group newDefaultGroup(String tenantId) {
        Group defaultGroup = new Group();
        defaultGroup.setGroupId(DEFAULT);
        defaultGroup.setGroupName(DEFAULT_GROUP_NAME);
        defaultGroup.setTenantId(tenantId);
        return defaultGroup;
    }

    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    @Override
    public void batchAddRoleUser(LoginUser user, BatchAddRoleUser req) {
        final String authority = req.getRoleId();
        final LambdaQueryWrapper<UserRoleDao> query = Wrappers.lambdaQuery(UserRoleDao.class);
        final List<String> usernames = req.getUsername();
        query.eq(UserRoleDao::getAuthority, req.getRoleId());
        final List<UserRoleDao> dbResult = userRolesMapper.selectList(query);
        final Set<String> dbUsernames =
                dbResult.stream().map(UserRoleDao::getUserid).collect(Collectors.toSet());
        if (usernames.size() >= dbUsernames.size()) {
            usernames.removeAll(dbUsernames);
            if (CollectionUtils.isNotEmpty(usernames)) {
                final String tenantId = user.getTenantId();
                final List<UserRoleDao> saveList = new ArrayList<>(usernames.size());
                usernames.forEach(username -> saveList.add(new UserRoleDao(username, authority, tenantId)));
                userRolesMapper.insert(saveList);
            }
        } else {
            if (CollectionUtils.isNotEmpty(usernames)) {
                dbUsernames.removeIf(usernames::contains);
            }
            userRolesMapper.batchDelete(authority, new ArrayList<>(dbUsernames));
        }
    }
}
