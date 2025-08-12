package com.lambda.fusion.authority.role.service;

import static com.lambda.fusion.authority.AuthorityConstants.CACHE_MANAGER;
import static com.lambda.fusion.authority.AuthorityConstants.DEFAULT_GROUP_NAME;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.resource.model.MutableResource;
import com.lambda.fusion.authority.resource.model.ResourceType;
import com.lambda.fusion.authority.resource.service.ResourceService;
import com.lambda.fusion.authority.role.mapper.AccessPermissionMapper;
import com.lambda.fusion.authority.role.mapper.GroupMapper;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.mapper.UserRolesMapper;
import com.lambda.fusion.authority.role.model.MutableRole;
import com.lambda.fusion.authority.role.model.domain.AccessPermissionDO;
import com.lambda.fusion.authority.role.model.dto.BatchAddRoleUserDTO;
import com.lambda.fusion.authority.role.model.entity.GroupEntity;
import com.lambda.fusion.authority.role.model.entity.UserRoleEntity;
import com.lambda.fusion.authority.role.model.vo.AccessPermissionVO;
import com.lambda.fusion.authority.role.model.vo.GroupRoleVo;
import com.lambda.fusion.authority.role.model.vo.GroupVo;
import com.lambda.fusion.authority.tenant.service.TenantAuthorizeManager;
import com.lambda.fusion.authority.utils.MybatisUtils;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.fusion.core.user.User;
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
    public List<MutableRole> getAllRoles(User operator) {
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(4);
        parameters.put("dev", operator.isDev());
        parameters.put(ADMIN, operator.isAdmin());
        parameters.put("userid", operator.getUsername());
        parameters.put(Constants.TENANT_ID, operator.getTenantId());
        return roleMapper.getAllRoles(parameters);
    }

    @SuppressWarnings("squid:S3776")
    @Override
    public List<GroupRoleVo> getAllGroupRoles(User operator, String tenantId) {
        if (StringUtils.isBlank(tenantId) || StringUtils.isNotBlank(operator.getTenantId())) {
            tenantId = operator.getTenantId();
        }
        Set<String> excludes = Sets.newHashSet();
        excludes.add(Constants.ROLE_USER);
        excludes.add(Constants.ROLE_HMAC);
        excludes.add(Constants.ROLE_SYSTEM);
        excludes.add(Constants.ROLE_TENANT);
        if (!operator.isDev()) {
            excludes.add(Constants.ROLE_DEV);
            if (!operator.isAdmin()) {
                excludes.add(Constants.ROLE_ADMIN);
            }
        }
        Set<String> queryExclude = internalRoleService.queryExclude(operator);
        excludes.addAll(queryExclude);
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(2);
        parameters.put("excludes", excludes);
        parameters.put(Constants.TENANT_ID, tenantId);
        List<MutableRole> roles = roleMapper.getAllRoles(parameters);
        List<GroupEntity> groupEntities = groupMapper.getAllGroup(parameters);
        GroupEntity defaultGroupEntity = newDefaultGroup(tenantId);
        if (notcontains(groupEntities, defaultGroupEntity)) {
            groupEntities.add(defaultGroupEntity);
        }
        // Jin 如果当前用户的角色是开发工程师 只返回ROLE_DEV和ROLE_ADMIN
        final Map<String, List<MutableRole>> map = roles.stream()
                .filter(mutableRole -> {
                    if (operator.isDev()) {
                        return (mutableRole.getAuthority().equals(Constants.ROLE_DEV)
                                || mutableRole.getAuthority().equals(Constants.ROLE_ADMIN));
                    } else {
                        return true;
                    }
                })
                .collect(Collectors.groupingBy(MutableRole::getGroupId));

        List<GroupRoleVo> result = new ArrayList<>();
        groupEntities.forEach(item -> {
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
    public MutableRole updateRole(User operator, MutableRole role) {
        Assert.notNull(role, "role不能为空");
        Assert.notNull(role.getAlias(), "别名不能为空！");
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
    public MutableRole saveRole(User operator, MutableRole role) {
        Assert.notNull(role, "role不能为空");
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
        Assert.notNull(authority, "role name 不能为空");
        if (authority.startsWith(Constants.ROLE_TENANT)) {
            authority = Constants.ROLE_TENANT;
        }
        return roleMapper.getRoleByAuthority(authority);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoleById(String authority) {
        Assert.notNull(authority, "role name 不能为空");
        Set<String> excludes = Stream.of(BUILT_IN_ROLES).collect(Collectors.toSet());
        Set<String> deleteExclude = internalRoleService.deleteExclude(OperatorUtils.getOperator());
        excludes.addAll(deleteExclude);
        Assert.isTrue(!excludes.contains(authority), "角色" + authority + "不能删除");
        MutableRole role = getRoleByAuthority(authority);
        authority = role.getAuthority();
        Assert.notNull(role, "角色" + authority + "不存在");
        boolean hasUsedAuthority = hasUsedAuthority(authority);
        Assert.isTrue(!hasUsedAuthority, "角色" + authority + "已被使用");
        roleMapper.deleteRoleByAuthority(authority);
        roleMapper.deleteResourceRoleByAuthority(authority);
        roleMapper.deleteRoleByAuthority(authority);
        roleMapper.deleteUserRoleByAuthority(authority);
    }

    @Override
    public boolean hasExists(String authority) {
        Assert.notNull(authority, "role name 不能为空");
        return roleMapper.hasExists(authority);
    }

    @Override
    public List<AccessPermissionVO> getAccessPermissions(User operator, String authority, Integer mode) {
        Assert.notNull(authority, "role name 不能为空");
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(3);
        parameters.put("authority", authority);
        parameters.put("mode", Optional.ofNullable(mode).orElse(0));
        if (!operator.isDev()) {
            StpUtil.checkPermission(authority);
            Set<String> authorities = operator.getPermissions();
            authorities.add(operator.getUsername());
            parameters.put("authorities", authorities);
        }
        List<AccessPermissionVO> permissions = roleMapper.getAccessPermissions(parameters);
        if (CollectionUtils.isEmpty(permissions)) {
            return Collections.emptyList();
        }
        return TreeFactory.build(permissions);
    }

    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAuthorization(String authority, String resourceid, int status, User operator) {
        Assert.notNull(authority, "role name 不能为空");
        Assert.notNull(resourceid, "资源id不能为空！");
        if (!operator.isDev()) {
            StpUtil.checkPermission(authority);
        }
        String tenantId = operator.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            //        todo    tenantId = RoleUtil.getTenantId(authority);
        }
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
                MybatisUtils.batchInsert(differently, RoleMapper.class, (id, mapper) -> {
                    AccessPermissionDO parameters = new AccessPermissionDO();
                    parameters.setAuthority(authority);
                    parameters.setTenantId(tenantId);
                    parameters.setStatus(status);
                    parameters.setId(id);
                    mapper.saveAuthorization(parameters);
                });
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
    public void deleteAuthorization(String authority, String resourceid, User operator) {
        Assert.notNull(authority, "role name 不能为空");
        Assert.notNull(resourceid, "资源id不能为空！");
        if (!operator.isDev()) {
            StpUtil.checkPermission(authority);
        }
        String tenantId = operator.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            // todo  tenantId = RoleUtil.getTenantId(authority);
        }
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
        Assert.notNull(authority, "role name 不能为空");
        MutableRole role = getRoleByAuthority(authority);
        Assert.notNull(role, " 角色" + authority + "不存在");
        roleMapper.prohibitRole(type, role.getAuthority());
    }

    @Override
    public List<MutableRole> getTenantRolesByOwner(String owner) {
        return roleMapper.getTenantRolesByOwner(owner);
    }

    @Override
    public GroupVo addGroup(GroupVo groupVo) {
        groupVo.setGroupId(IdWorker.getIdStr());
        groupMapper.insert(BeanUtil.copyProperties(groupVo, GroupEntity.class));
        return groupVo;
    }

    @Override
    public void deleteGroup(String groupId) {
        Assert.isFalse(Objects.equals(groupId, DEFAULT), "默认组不能删除");
        groupMapper.updateRoleGroupId(groupId, DEFAULT);
        groupMapper.deleteById(groupId);
    }

    @Override
    public GroupVo updateGroup(GroupVo groupVo) {
        groupMapper.updateById(BeanUtil.copyProperties(groupVo, GroupEntity.class));
        return groupVo;
    }

    @Override
    public GroupVo getGroupById(String id) {
        GroupEntity groupEntity = groupMapper.selectById(id);
        if (groupEntity != null) {
            GroupVo target = new GroupVo();
            BeanUtil.copyProperties(groupEntity, target);
            return target;
        }
        return null;
    }

    @Override
    public List<GroupVo> listGroups(User operator) {
        String tenantId = operator.getTenantId();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(1);
        parameters.put(Constants.TENANT_ID, tenantId);
        GroupEntity defaultGroupEntity = newDefaultGroup(tenantId);
        List<GroupEntity> groupEntities = groupMapper.getAllGroup(parameters);
        if (notcontains(groupEntities, defaultGroupEntity)) {
            groupEntities.add(defaultGroupEntity);
        }
        return BeanUtil.copyToList(groupEntities, GroupVo.class);
    }

    private boolean notcontains(List<GroupEntity> groupEntities, GroupEntity defaultGroupEntity) {
        return CollectionUtils.isEmpty(groupEntities)
                || (CollectionUtils.isNotEmpty(groupEntities) && !groupEntities.contains(defaultGroupEntity));
    }

    private GroupEntity newDefaultGroup(String tenantId) {
        GroupEntity defaultGroupEntity = new GroupEntity();
        defaultGroupEntity.setGroupId(DEFAULT);
        defaultGroupEntity.setGroupName(DEFAULT_GROUP_NAME);
        defaultGroupEntity.setTenantId(tenantId);
        return defaultGroupEntity;
    }

    @CacheEvict(value = "LAResourceOwners", allEntries = true, cacheManager = CACHE_MANAGER)
    @Override
    public void batchAddRoleUser(User user, BatchAddRoleUserDTO req) {
        final String authority = req.getRoleId();
        final LambdaQueryWrapper<UserRoleEntity> query = Wrappers.lambdaQuery(UserRoleEntity.class);
        final List<String> usernames = req.getUsername();
        query.eq(UserRoleEntity::getAuthority, req.getRoleId());
        final List<UserRoleEntity> dbResult = userRolesMapper.selectList(query);
        final Set<String> dbUsernames =
                dbResult.stream().map(UserRoleEntity::getUserid).collect(Collectors.toSet());
        if (usernames.size() >= dbUsernames.size()) {
            usernames.removeAll(dbUsernames);
            if (CollectionUtils.isNotEmpty(usernames)) {
                final String tenantId = user.getTenantId();
                final List<UserRoleEntity> saveList = new ArrayList<>(usernames.size());
                usernames.forEach(username -> saveList.add(new UserRoleEntity(username, authority, tenantId)));
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
