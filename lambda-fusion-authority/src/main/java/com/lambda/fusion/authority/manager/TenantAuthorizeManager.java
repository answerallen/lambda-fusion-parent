package com.lambda.fusion.authority.manager;

import static com.lambda.fusion.core.FusionConstants.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.mapper.ResourceMapper;
import com.lambda.fusion.authority.mapper.RoleMapper;
import com.lambda.fusion.authority.mapper.UserInfoMapper;
import com.lambda.fusion.authority.mapper.UserMapper;
import com.lambda.fusion.authority.model.resource.Resource;
import com.lambda.fusion.authority.model.role.SimpleRole;
import com.lambda.fusion.authority.model.tenant.TenantEntity;
import com.lambda.fusion.authority.model.user.ResetPassword;
import com.lambda.fusion.authority.model.user.User;
import com.lambda.fusion.authority.service.TenantService;
import com.lambda.fusion.authority.service.UserService;
import com.lambda.fusion.core.utils.SecurityUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
@SuppressFBWarnings({"UUF_UNUSED_FIELD", "NP_UNWRITTEN_FIELD", "UPM_UNCALLED_PRIVATE_METHOD"})
@Slf4j
public class TenantAuthorizeManager {

    private TenantService tenantService;
    private UserService userService;
    private UserMapper userMapper;
    private UserInfoMapper userInfoMapper;
    private ResourceMapper resourceMapper;
    private PasswordEncoder passwordEncoder;
    private RoleMapper roleMapper;

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
    public void saveAuth(String authority, List<Resource> resources, int status) {
        // 只处理租户管理员角色
        if (!isTenantAdmin(authority)) {
            return;
        }

        // 租户管理员的authority格式是ROLE_TENANT@tenantId

    }

    /**
     * 删除租户映射主库中的管理员角色权限
     *
     * @param authority 角色
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteAuthorization(String authority, List<Resource> resources) {
        // 只处理租户管理员角色
        if (!isTenantAdmin(authority)) {
            return;
        }
        // 租户管理员的authority格式是ROLE_TENANT@tenantId

    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void addUser(User user) {
        if (!isTenantAdmin(user)) {
            return;
        }
        // 租户管理员的tenantId属性为null，其所属组织id才是租户id
        String tenantId = userMapper.selectTenantIdByUsername(user.getUsername());
        // 在租户库中视作管理员，不能有租户id
        user.setTenantId(null);
        List<SimpleRole> roles = new ArrayList<>();
        roles.add(new SimpleRole(ROLE_ADMIN));
        user.setAuthorities(roles);
        // todo 添加用户
        System.out.println(tenantId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateUser(User user) {
        // 租户管理员的tenantId属性为null，其所属组织id才是租户id
        String tenantId = userMapper.selectTenantIdByUsername(user.getUsername());
        // 在租户库中视作管理员，不能有租户id
        user.setTenantId(null);
        // todo 添加用户
        System.out.println(tenantId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteUser(String username) {
        User user = userService.getByUsername(username);
        if (!isTenantAdmin(user)) {
            return;
        }
        // 租户管理员的tenantId属性为null，其所属组织id才是租户id
        String tenantId = userMapper.selectTenantIdByUsername(user.getUsername());
        execute(tenantId, () -> userService.deleteUser(SecurityUtils.getUser(), username));
        System.out.println(tenantId);
    }

    public void resetPassword(ResetPassword resetPassword) {
        String username = resetPassword.getUsername();
        String newPassword = resetPassword.getNewPassword();
        if (StringUtils.isBlank(newPassword)) {
            return;
        }
        User user = userService.getByUsername(username);
        if (!isTenantAdmin(user)) {
            return;
        }

        // 租户管理员的tenantId属性为null，其所属组织id才是租户id
        String tenantId = userMapper.selectTenantIdByUsername(user.getUsername());
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        // 租户主库的密码要和主库的租户管理员密码同步
        System.out.println(tenantId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void prohibitUser(Integer type, String username) {
        User user = userService.getByUsername(username);
        if (!isTenantAdmin(user)) {
            return;
        }
        String tenantId = userMapper.selectTenantIdByUsername(user.getUsername());
        System.out.println(tenantId);
    }

    private void hasOperation(LoginUser operator, String tenantId) {
        String crrTenantId = operator.getTenantId();
        if (org.apache.commons.lang.StringUtils.isNotBlank(crrTenantId) && !crrTenantId.equals(tenantId)) {
            throw AuthorityBusinessException.authNoPermission();
        }
    }

    private boolean isTenantAdmin(String authority) {
        return ROLE_TENANT.equals(authority) || authority.startsWith(ROLE_TENANT + AT);
    }

    private boolean isTenantAdmin(User user) {
        boolean isTenantAdmin = false;
        List<SimpleRole> roles = user.getAuthorities();
        if (roles != null && !roles.isEmpty()) {
            // 判断是否为租户管理员
            isTenantAdmin = roles.stream().anyMatch(role -> isTenantAdmin(role.getAuthority()));
        }
        return isTenantAdmin;
    }

    /**
     * 在所有设置了主库映射的租户主库中执行
     *
     * @param runnable runnable
     */
    @SuppressWarnings("unused")
    private void execute(Runnable runnable) {
        List<String> tenantDsKey = getTenantDsKey();
        if (tenantDsKey.isEmpty()) {
            return;
        }
        // 依次操作租户库
        for (String dsKey : tenantDsKey) {
            try {
                if (StringUtils.isNotBlank(dsKey)) {
                    //                   DynamicDataSourceWrapper.wrap(dsKey, runnable);
                }
            } catch (Exception e) {
                log.error("租户主库执行异常，数据源id:{}", dsKey, e);
            }
        }
    }

    /**
     * 在指定租户主库中执行
     *
     * @param tenantId 租户id
     * @param runnable runnable
     */
    private void execute(String tenantId, Runnable runnable) {
        if (StringUtils.isBlank(tenantId)) {
            return;
        }
        // TODO 数据库执行
    }

    private List<String> getTenantIds() {
        LambdaQueryWrapper<TenantEntity> wrapper = Wrappers.lambdaQuery(TenantEntity.class)
                .eq(TenantEntity::getStatus, 1)
                .apply("EXAMINE_STATE = {0}", 1);
        List<TenantEntity> tenants = tenantService.list(wrapper);
        if (tenants == null || tenants.isEmpty()) {
            return new ArrayList<>();
        }
        return tenants.stream().map(TenantEntity::getTenantId).collect(Collectors.toList());
    }

    private List<String> getTenantDsKey() {
        List<String> tenantIds = getTenantIds();
        if (tenantIds == null || tenantIds.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }
}
