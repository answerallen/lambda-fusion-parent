package com.lambda.fusion.authority.role.service.impl;

import static com.lambda.fusion.authority.AuthorityConstants.DEFAULT_GROUP_NAME;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.resource.model.Resource;
import com.lambda.fusion.authority.resource.model.ResourceType;
import com.lambda.fusion.authority.resource.service.ResourceService;
import com.lambda.fusion.authority.role.mapper.AccessPermissionMapper;
import com.lambda.fusion.authority.role.mapper.GroupMapper;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.model.*;
import com.lambda.fusion.authority.role.service.InternalRoleService;
import com.lambda.fusion.authority.role.service.RoleService;
import com.lambda.fusion.authority.tenant.manager.TenantAuthorizeManager;
import com.lambda.fusion.authority.user.mapper.UserRoleMapper;
import com.lambda.fusion.authority.user.model.UserRoleEntity;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.LoginUserDetails;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;

    private final ResourceService resourceService;

    private final GroupMapper groupMapper;

    private final UserRoleMapper userRoleMapper;

    private final AccessPermissionMapper accessPermissionMapper;

    private final InternalRoleService internalRoleService;

    private TenantAuthorizeManager tenantAuthorizeManager;

    @Autowired(required = false)
    public void setTenantAuthorizeManager(TenantAuthorizeManager tenantAuthorizeManager) {
        this.tenantAuthorizeManager = tenantAuthorizeManager;
    }

    @Override
    public List<Role> getAllRoles(LoginUserDetails loginUserDetails) {
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(4);
        parameters.put(AuthorityConstants.DEV, loginUserDetails.isDev());
        parameters.put(AuthorityConstants.ADMIN, loginUserDetails.isAdmin());
        parameters.put(AuthorityConstants.USERNAME, loginUserDetails.getUsername());
        parameters.put(FusionConstants.TENANT_ID, loginUserDetails.getTenantId());
        return roleMapper.getAllRoles(parameters);
    }

    @Override
    public List<GroupRole> grouped(LoginUserDetails loginUserDetails, String tenantId) {
        if (StringUtils.isBlank(tenantId) || StringUtils.isNotBlank(loginUserDetails.getTenantId())) {
            tenantId = loginUserDetails.getTenantId();
        }
        Set<String> excludes = Sets.newHashSet();
        excludes.add(FusionConstants.ROLE_USER);
        excludes.add(FusionConstants.ROLE_HMAC);
        excludes.add(FusionConstants.ROLE_SYSTEM);
        excludes.add(FusionConstants.ROLE_TENANT);
        if (!loginUserDetails.isDev()) {
            excludes.add(FusionConstants.ROLE_DEV);
            if (!loginUserDetails.isAdmin()) {
                excludes.add(FusionConstants.ROLE_ADMIN);
            }
        }
        Set<String> queryExclude = internalRoleService.queryExclude(loginUserDetails);
        excludes.addAll(queryExclude);
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(2);
        parameters.put(FusionConstants.EXCLUDES, excludes);
        parameters.put(FusionConstants.TENANT_ID, tenantId);
        List<Role> roles = roleMapper.getAllRoles(parameters);
        List<GroupEntity> groupEntities = groupMapper.getAllGroup(parameters);
        GroupEntity defaultGroupEntity = newDefaultGroup(tenantId);
        if (notContains(groupEntities, defaultGroupEntity)) {
            groupEntities.add(defaultGroupEntity);
        }
        // Jin 如果当前用户的角色是开发工程师 只返回ROLE_DEV和ROLE_ADMIN
        final Map<String, List<Role>> map = roles.stream()
                .filter(mutableRole -> {
                    if (loginUserDetails.isDev()) {
                        return (mutableRole.getAuthority().equals(FusionConstants.ROLE_DEV)
                                || mutableRole.getAuthority().equals(FusionConstants.ROLE_ADMIN));
                    } else {
                        return true;
                    }
                })
                .collect(Collectors.groupingBy(Role::getGroupId));

        List<GroupRole> result = new ArrayList<>();
        groupEntities.forEach(item -> {
            GroupRole group = GroupRole.fromEntity(GroupRole.class, item);
            List<Role> children = map.get(group.getGroupId());
            if (CollectionUtils.isNotEmpty(children)) {
                group.setRoles(children);
                group.setDisableAssignment(false);
                group.setDisableOperations(false);
            } else {
                group.setDisableAssignment(false);
                group.setDisableOperations(true);
            }
            result.add(group);
        });
        return result;
    }

    @Override
    public Page<Role> getAllRoles(Page<Role> pageable, Map<String, Object> parameters) {
        pageable = roleMapper.getAllMutableRoles(pageable, parameters);
        List<Role> roles = pageable.getRecords();
        if (CollectionUtils.isNotEmpty(roles)) {
            for (Role item : roles) {
                String authority = item.getAuthority();
                item.setBuiltIn(AuthorityConstants.BUILT_IN_ROLES.contains(authority));
            }
        }
        return pageable;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role updateRole(LoginUserDetails loginUserDetails, UpdateRole updateRole) {
        if (updateRole == null || updateRole.getAlias() == null) {
            throw AuthorityBusinessException.invalidParameter("别名不能为空！");
        }
        RoleEntity roleEntity = updateRole.toEntity();
        roleMapper.updateById(roleEntity);
        return getRoleByAuthority(updateRole.getAuthority());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role saveRole(LoginUserDetails loginUserDetails, CreateRole createRole) {
        if (createRole == null || createRole.getAlias() == null) {
            throw AuthorityBusinessException.invalidParameter("别名不能为空！");
        }
        String tenantId = loginUserDetails.getTenantId();
        String authority = UUID.fastUUID().toString();
        // 注意：此处逻辑有问题，新生成的UUID不会存在，但保留原有逻辑
        createRole.setAuthority(authority);
        createRole.setAuthority(authority);
        createRole.setTenantId(tenantId);
        String groupId = Optional.ofNullable(createRole.getGroupId()).orElse(AuthorityConstants.DEFAULT);
        createRole.setGroupId(groupId);
        RoleEntity roleEntity = createRole.toEntity();
        roleEntity.setCreatedBy(loginUserDetails.getName());
        roleEntity.setCreatedAt(LocalDateTime.now());
        roleMapper.insert(roleEntity);
        return getRoleByAuthority(authority);
    }

    @Override
    public Role getRoleByAuthority(String authority) {
        if (authority == null) {
            throw AuthorityBusinessException.invalidParameter("角色标识不能为空");
        }
        if (authority.startsWith(FusionConstants.ROLE_TENANT)) {
            authority = FusionConstants.ROLE_TENANT;
        }
        return roleMapper.getRoleByAuthority(authority);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRoleById(String authority) {
        if (authority == null) {
            throw AuthorityBusinessException.invalidParameter("角色标识不能为空");
        }
        Set<String> excludes = new HashSet<>(AuthorityConstants.BUILT_IN_ROLES);
        Set<String> deleteExclude = internalRoleService.deleteExclude(OperatorUtils.getOperator());
        excludes.addAll(deleteExclude);
        if (excludes.contains(authority)) {
            throw AuthorityBusinessException.operationNotSupported("角色" + authority + "不能删除");
        }
        Role role = getRoleByAuthority(authority);
        if (role == null) {
            throw AuthorityBusinessException.roleNotFound(authority);
        }
        authority = role.getAuthority();
        boolean hasUsedAuthority = hasUsedAuthority(authority);
        if (hasUsedAuthority) {
            throw AuthorityBusinessException.roleAssignedToUser(authority);
        }
        roleMapper.deleteRoleByAuthority(authority);
        roleMapper.deleteResourceRoleByAuthority(authority);
        roleMapper.deleteUserRoleByAuthority(authority);
    }

    @Override
    public boolean hasExists(String authority) {
        if (authority == null) {
            throw AuthorityBusinessException.invalidParameter("角色标识不能为空");
        }
        return roleMapper.hasExists(authority);
    }

    @Override
    public List<AccessPermission> getAccessPermission(
            LoginUserDetails loginUserDetails, String authority, Integer mode) {
        if (authority == null) {
            throw AuthorityBusinessException.invalidParameter("角色标识不能为空");
        }
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(3);
        parameters.put("authority", authority);
        parameters.put("mode", Optional.ofNullable(mode).orElse(0));
        if (!loginUserDetails.isDev()) {
            StpUtil.checkPermission(authority);
            Set<String> authorities = loginUserDetails.getRoles();
            authorities.add(loginUserDetails.getUsername());
            parameters.put("authorities", authorities);
        }
        List<AccessPermission> permissions = roleMapper.getAccessPermission(parameters);
        if (CollectionUtils.isEmpty(permissions)) {
            return Collections.emptyList();
        }
        return TreeBuilder.build(permissions);
    }

    @CacheEvict(value = "ResourceOwners", allEntries = true)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantRolePermission(String authority, String resourceId, int status, LoginUserDetails loginUser) {
        if (authority == null) {
            throw AuthorityBusinessException.invalidParameter("角色标识不能为空");
        }
        if (resourceId == null) {
            throw AuthorityBusinessException.invalidParameter("资源id不能为空");
        }
        if (!loginUser.isDev()) {
            // TODO 判断是否为自身权限
            log.info("非开发者");
        }
        String tenantId = loginUser.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            // todo tenantId = RoleUtil.getTenantId(authority);
            log.info("无租户");
        }
        Resource resource = resourceService.getResourceById(resourceId);
        if (resource == null) {
            throw AuthorityBusinessException.resourceNotFound(resourceId);
        }
        List<Resource> resources = resourceService.getAllParentsByOperator(loginUser, resource);
        List<Resource> children = resourceService.getAllChildrenByOperator(loginUser, resource);
        resources.add(resource);
        resources.addAll(children);
        Set<String> ids = resources.stream().map(Resource::getId).collect(Collectors.toSet());
        List<String> has = roleMapper.hasAuthorizedWithIntersection(authority, ids);
        Set<String> authorized = Sets.newHashSet(has);

        Set<String> intersection = Sets.intersection(ids, authorized);
        if (CollectionUtils.isNotEmpty(intersection)) {
            AuthorityPermission authorityPermission = new AuthorityPermission();
            authorityPermission.setAuthority(authority);
            authorityPermission.setIds(intersection);
            authorityPermission.setTenantId(tenantId);
            authorityPermission.setStatus(status);
            roleMapper.batchUpdateAuthorization(authorityPermission);
        }
        Set<String> diff = Sets.difference(ids, authorized);
        if (!CollectionUtils.isEmpty(diff)) {
            List<AuthorityPermission> list = new ArrayList<>(diff.size());
            for (String id : diff) {
                AuthorityPermission authorityPermission = new AuthorityPermission();
                authorityPermission.setAuthority(authority);
                authorityPermission.setTenantId(tenantId);
                authorityPermission.setStatus(status);
                authorityPermission.setId(id);
                list.add(authorityPermission);
            }
            roleMapper.batchSaveAuthorization(list);
        }

        // 处理租户主库
        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.saveAuth(authority, resources, status);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "ResourceOwners", allEntries = true)
    public void revokeRolePermission(String authority, String resourceId, LoginUserDetails loginUserDetails) {
        if (authority == null) {
            throw AuthorityBusinessException.invalidParameter("角色标识不能为空");
        }
        if (resourceId == null) {
            throw AuthorityBusinessException.invalidParameter("资源id不能为空");
        }
        if (!loginUserDetails.isDev()) {
            StpUtil.checkPermission(authority);
        }
        String tenantId = loginUserDetails.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            // todo tenantId = RoleUtil.getTenantId(authority);
            log.info("非租户");
        }
        Resource resource = resourceService.getResourceById(resourceId);
        if (resource == null) {
            throw AuthorityBusinessException.resourceNotFound(resourceId);
        }
        List<Resource> resources = resourceService.getAllChildrenByOperator(loginUserDetails, resource);
        resources.add(resource);
        List<String> ids = resources.stream().map(Resource::getId).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(ids)) {
            roleMapper.batchDeleteAuthorization(authority, ids, tenantId);
        }
        ResourceType resourceType = ResourceType.get(resource.getResType());
        if (resourceType != null && !resourceType.isButton()) {
            List<Resource> parents = resourceService.getAllParentsByOperator(loginUserDetails, resource);
            for (Resource parent : parents) {
                AuthorityPermission parameters = new AuthorityPermission();
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

    @Override
    public boolean hasUsedAuthority(String authority) {
        return roleMapper.hasUsedAuthority(authority);
    }

    @Override
    public void prohibitRole(int type, String authority) {
        if (authority == null) {
            throw AuthorityBusinessException.invalidParameter("角色标识不能为空");
        }
        Role role = getRoleByAuthority(authority);
        if (role == null) {
            throw AuthorityBusinessException.roleNotFound(authority);
        }
        roleMapper.prohibitRole(type, role.getAuthority());
    }

    @Override
    public List<Role> getTenantRolesByOwner(String owner) {
        return roleMapper.getTenantRolesByOwner(owner);
    }

    @Override
    public Group addGroup(Group group) {
        group.setGroupId(IdWorker.getIdStr());
        groupMapper.insert(BeanUtil.copyProperties(group, GroupEntity.class));
        return group;
    }

    @Override
    public void deleteGroup(String groupId) {
        if (Objects.equals(groupId, AuthorityConstants.DEFAULT)) {
            throw AuthorityBusinessException.operationNotSupported("默认组不能删除");
        }
        groupMapper.updateRoleGroupId(groupId, AuthorityConstants.DEFAULT);
        groupMapper.deleteById(groupId);
    }

    @Override
    public Group updateGroup(Group group) {
        groupMapper.updateById(BeanUtil.copyProperties(group, GroupEntity.class));
        return group;
    }

    @Override
    public Group getGroupById(String id) {
        GroupEntity groupEntity = groupMapper.selectById(id);
        if (groupEntity != null) {
            Group target = new Group();
            BeanUtil.copyProperties(groupEntity, target);
            return target;
        }
        return null;
    }

    @Override
    public List<Group> listGroups(LoginUserDetails loginUserDetails) {
        String tenantId = loginUserDetails.getTenantId();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(1);
        parameters.put(FusionConstants.TENANT_ID, tenantId);
        GroupEntity defaultGroupEntity = newDefaultGroup(tenantId);
        List<GroupEntity> groupEntities = groupMapper.getAllGroup(parameters);
        if (notContains(groupEntities, defaultGroupEntity)) {
            groupEntities.add(defaultGroupEntity);
        }
        return BeanUtil.copyToList(groupEntities, Group.class);
    }

    private boolean notContains(List<GroupEntity> groupEntities, GroupEntity defaultGroupEntity) {
        return CollectionUtils.isEmpty(groupEntities)
                || (CollectionUtils.isNotEmpty(groupEntities) && !groupEntities.contains(defaultGroupEntity));
    }

    private GroupEntity newDefaultGroup(String tenantId) {
        GroupEntity defaultGroupEntity = new GroupEntity();
        defaultGroupEntity.setGroupId(AuthorityConstants.DEFAULT);
        defaultGroupEntity.setGroupName(DEFAULT_GROUP_NAME);
        defaultGroupEntity.setTenantId(tenantId);
        return defaultGroupEntity;
    }

    @CacheEvict(value = "ResourceOwners", allEntries = true)
    @Override
    public void assignUsersToRole(LoginUserDetails loginUserDetails, BatchRoleUserAssignmentRequest req) {
        final String authority = req.getRoleId();
        final List<String> usernames = req.getUsername();
        final List<UserRoleEntity> dbResult = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getAuthority, req.getRoleId()));
        final Set<String> dbUsernames =
                dbResult.stream().map(UserRoleEntity::getUsername).collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(usernames)) {
            // 如果传入为空，则删除所有
            if (CollectionUtils.isNotEmpty(dbUsernames)) {
                userRoleMapper.batchDelete(authority, new ArrayList<>(dbUsernames));
            }
            return;
        }

        // 1. 计算需要添加的用户 (usernames - dbUsernames)
        List<String> toAdd = new ArrayList<>(usernames);
        toAdd.removeAll(dbUsernames);

        // 2. 计算需要删除的用户 (dbUsernames - usernames)
        List<String> toDelete = new ArrayList<>(dbUsernames);
        toDelete.removeAll(usernames);

        // 3. 执行添加
        if (CollectionUtils.isNotEmpty(toAdd)) {
            final String tenantId = loginUserDetails.getTenantId();
            final List<UserRoleEntity> saveList = new ArrayList<>(toAdd.size());
            toAdd.forEach(username -> saveList.add(new UserRoleEntity(username, authority, tenantId)));
            userRoleMapper.insert(saveList);
        }

        // 4. 执行删除
        if (CollectionUtils.isNotEmpty(toDelete)) {
            userRoleMapper.batchDelete(authority, toDelete);
        }
    }
}
