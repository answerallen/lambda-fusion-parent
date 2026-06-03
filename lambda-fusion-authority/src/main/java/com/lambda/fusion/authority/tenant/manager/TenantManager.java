package com.lambda.fusion.authority.tenant.manager;

import static com.lambda.fusion.core.FusionConstants.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.resource.model.Resource;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.model.AuthorityPermission;
import com.lambda.fusion.authority.role.model.UserAuthority;
import com.lambda.fusion.authority.tenant.model.TenantEntity;
import com.lambda.fusion.authority.tenant.service.TenantService;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.mapper.UserRoleMapper;
import com.lambda.fusion.authority.user.model.ResetPassword;
import com.lambda.fusion.authority.user.model.User;
import com.lambda.fusion.authority.user.model.entity.UserEntity;
import com.lambda.fusion.authority.user.model.entity.UserInfoEntity;
import com.lambda.fusion.authority.user.model.entity.UserRoleEntity;
import com.lambda.fusion.authority.utils.AuthorityHelper;
import com.lambda.fusion.core.utils.AuthUtils;
import com.lambda.fusion.datasource.commons.api.DataSourceSwitcher;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户授权数据管理器
 * <pre>
 * 依次在各个租户的主库映射库中执行相关数据的增删改操作
 * 资源：同步增删改
 * 角色权限：仅同步ROLE_TENANT相关的权限操作，在租户主库中，操作的是ROLE_ADMIN
 * 用户：同步增删改，租户管理员在租户主库中，视作管理员
 *
 * </pre>
 *
 *
 */
@Slf4j
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class TenantManager {

    private final TenantService tenantService;
    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleMapper roleMapper;
    private final DataSourceManageService dataSourceManageService;

    /**
     * 保存租户映射主库中的管理员角色权限
     * <pre>
     * 租户管理员在租户主库中，视作管理员角色
     * 租户主库里的资源，就是租户管理员所拥有的资源
     * 需要根据租户管理员的权限来对资源表数据进行操作
     * </pre>
     *
     * @param authority 角色
     * @param resources 相关的资源列表
     * @param status    状态
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void grantRolePermission(String authority, List<Resource> resources, int status) {
        if (isNotTenantAdmin(authority) || resources == null || resources.isEmpty()) {
            return;
        }
        Set<String> resourceIds = resources.stream()
                .map(Resource::getId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (resourceIds.isEmpty()) {
            return;
        }
        for (String tenantId : resolveTenantIds(authority)) {
            executeInTenantDataSource(tenantId, () -> processRolePermissionGrant(resourceIds, status));
        }
    }

    private void processRolePermissionGrant(Set<String> resourceIds, int status) {
        List<String> exists = roleMapper.hasAuthorizedWithIntersection(ROLE_ADMIN, resourceIds);
        Set<String> authorized = exists == null ? Set.of() : Set.copyOf(exists);

        Set<String> intersection =
                resourceIds.stream().filter(authorized::contains).collect(Collectors.toSet());
        if (!intersection.isEmpty()) {
            AuthorityPermission updatePayload = new AuthorityPermission();
            updatePayload.setAuthority(ROLE_ADMIN);
            updatePayload.setIds(intersection);
            updatePayload.setTenantId(null);
            updatePayload.setStatus(status);
            roleMapper.batchUpdateAuthorization(updatePayload);
        }

        List<AuthorityPermission> toInsert = resourceIds.stream()
                .filter(id -> !authorized.contains(id))
                .map(id -> {
                    AuthorityPermission permission = new AuthorityPermission();
                    permission.setAuthority(ROLE_ADMIN);
                    permission.setId(id);
                    permission.setStatus(status);
                    permission.setTenantId(null);
                    return permission;
                })
                .collect(Collectors.toList());
        if (!toInsert.isEmpty()) {
            roleMapper.batchSaveAuthorization(toInsert);
        }
    }

    /**
     * 删除租户映射主库中的管理员角色权限
     *
     * @param authority 角色
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void revokeRolePermission(String authority, List<Resource> resources) {
        if (isNotTenantAdmin(authority) || resources == null || resources.isEmpty()) {
            return;
        }
        List<String> resourceIds = resources.stream()
                .map(Resource::getId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (resourceIds.isEmpty()) {
            return;
        }
        for (String tenantId : resolveTenantIds(authority)) {
            executeInTenantDataSource(
                    tenantId, () -> roleMapper.batchDeleteAuthorization(ROLE_ADMIN, resourceIds, null));
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void addUser(User user) {
        String tenantId = resolveTenantIdForTenantAdmin(user);
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        UserInfoEntity sourceUserInfo = userInfoMapper.selectById(user.getUsername());
        executeInTenantDataSource(tenantId, () -> syncTenantAdminUser(user, sourceUserInfo));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateUser(User user) {
        String tenantId = resolveTenantIdForTenantAdmin(user);
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        UserInfoEntity sourceUserInfo = userInfoMapper.selectById(user.getUsername());
        executeInTenantDataSource(tenantId, () -> syncTenantAdminUser(user, sourceUserInfo));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteUser(String username) {
        String tenantId = getTenantIdIfAdmin(username);
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        executeInTenantDataSource(tenantId, () -> {
            userRoleMapper.deleteUserRoles(username);
            userInfoMapper.deleteById(username);
            userMapper.deleteById(username);
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void resetPassword(ResetPassword resetPassword) {
        String username = resetPassword.getUsername();
        String newPassword = resetPassword.getNewPassword();
        if (StringUtils.isBlank(newPassword)) {
            return;
        }
        String tenantId = getTenantIdIfAdmin(username);
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        executeInTenantDataSource(tenantId, () -> {
            userMapper.updatePassword(username, passwordEncoder.encode(newPassword));
            int count = userInfoMapper.updateStatus(username, true);
            if (count == 0) {
                UserInfoEntity userInfoEntity = new UserInfoEntity();
                userInfoEntity.setUsername(username);
                userInfoEntity.setUpdatePwd(true);
                userInfoMapper.insert(userInfoEntity);
            }
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void prohibitUser(Integer type, String username) {
        String tenantId = getTenantIdIfAdmin(username);
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        executeInTenantDataSource(tenantId, () -> userMapper.deactivateUser(type, username));
    }

    private String getTenantIdIfAdmin(String username) {
        if (isNotTenantAdmin(username)) {
            return null;
        }
        return userMapper.selectTenantIdByUsername(username);
    }

    private void hasOperation(LoginUser operator, String tenantId) {
        String crrTenantId = operator.getTenantId();
        if (org.apache.commons.lang.StringUtils.isNotBlank(crrTenantId) && !crrTenantId.equals(tenantId)) {
            throw AuthorityBusinessException.authNoPermission();
        }
    }

    private boolean isNotTenantAdmin(String username) {
        if (StringUtils.isBlank(username)) {
            return true;
        }
        return roleMapper.getUserAuthorityByUsername(username).stream()
                .map(UserAuthority::getAuthority)
                .filter(StringUtils::isNotBlank)
                .noneMatch(AuthorityHelper::isTenantAdminRole);
    }

    private String resolveTenantIdForTenantAdmin(User user) {
        if (!AuthorityHelper.hasTenantAdminRole(user) || StringUtils.isBlank(user.getUsername())) {
            return null;
        }
        return userMapper.selectTenantIdByUsername(user.getUsername());
    }

    private List<String> getTenantIds() {
        LambdaQueryWrapper<TenantEntity> wrapper =
                Wrappers.lambdaQuery(TenantEntity.class).eq(TenantEntity::getStatus, 1);
        List<TenantEntity> tenants = tenantService.list(wrapper);
        if (tenants == null || tenants.isEmpty()) {
            return new ArrayList<>();
        }
        return tenants.stream().map(TenantEntity::getTenantId).collect(Collectors.toList());
    }

    private List<String> resolveTenantIds(String authority) {
        if (ROLE_TENANT.equals(authority)) {
            return getTenantIds();
        }
        String tenantId = AuthorityHelper.getTenantId(authority);
        if (StringUtils.isBlank(tenantId)) {
            return new ArrayList<>();
        }
        return List.of(tenantId);
    }

    private void executeInTenantDataSource(String tenantId, Runnable command) {
        if (StringUtils.isBlank(tenantId) || command == null) {
            return;
        }
        LoginUser operator = AuthUtils.getUser();
        if (operator != null) {
            hasOperation(operator, tenantId);
        }
        String datasourceId = resolveTenantDatasourceId(tenantId);
        if (StringUtils.isBlank(datasourceId)) {
            return;
        }
        try (DataSourceSwitcher ignored = DataSourceSwitcher.switchTo(datasourceId)) {
            command.run();
        } catch (Exception exception) {
            log.error("租户主库同步失败 tenantId={} datasourceId={}", tenantId, datasourceId, exception);
            throw exception;
        }
    }

    private String resolveTenantDatasourceId(String tenantId) {
        TenantDataSourceEntity binding =
                dataSourceManageService.getTenantDataSource(tenantId, DatabaseUsageType.TENANT);
        if (binding == null || StringUtils.isBlank(binding.getDatasourceId())) {
            log.warn("租户主库映射缺失 tenantId={}", tenantId);
            return null;
        }
        return binding.getDatasourceId();
    }

    private void syncTenantAdminUser(User source, UserInfoEntity sourceUserInfo) {
        String username = source.getUsername();
        UserEntity entity = userMapper.selectById(username);
        if (entity == null) {
            entity = new UserEntity();
            entity.setUsername(username);
            entity.setCreatedAt(source.getCreatedAt() == null ? new Date() : source.getCreatedAt());
            entity.setCreatedBy(source.getCreatedBy());
        }
        entity.setPassword(source.getPassword());
        entity.setNickname(source.getNickname());
        entity.setMobile(source.getMobile());
        entity.setEmail(source.getEmail());
        entity.setEnabled(source.isEnabled() ? ENABLED : DISABLED);
        entity.setTenantId(null);
        entity.setExpiredTime(source.getExpiredTime());
        if (userMapper.selectById(username) == null) {
            try {
                userMapper.insert(entity);
            } catch (DuplicateKeyException duplicateKeyException) {
                userMapper.updateById(entity);
            }
        } else {
            userMapper.updateById(entity);
        }
        userRoleMapper.deleteUserRoles(username);
        userRoleMapper.insert(new UserRoleEntity(username, ROLE_ADMIN, null));
        syncUserInfo(username, sourceUserInfo);
    }

    private void syncUserInfo(String username, UserInfoEntity source) {
        UserInfoEntity target = userInfoMapper.selectById(username);
        boolean isNew = target == null;
        if (isNew) {
            target = new UserInfoEntity();
            target.setUsername(username);
        }

        if (source != null) {
            target.setAvatar(source.getAvatar());
            target.setRemark(source.getRemark());
            target.setIdentityId(source.getIdentityId());
            target.setPosition(source.getPosition());
            target.setStatus(source.getStatus());
            target.setEmpNo(source.getEmpNo());
            target.setDdNo(source.getDdNo());
            target.setDdNick(source.getDdNick());
            target.setWechatNo(source.getWechatNo());
            target.setUpdatePwd(source.getUpdatePwd());
            target.setExtendParam(source.getExtendParam());
            target.setWechatName(source.getWechatName());
        }
        target.setTenantId(null);

        if (isNew) {
            userInfoMapper.insert(target);
        } else {
            userInfoMapper.updateById(target);
        }
    }
}
