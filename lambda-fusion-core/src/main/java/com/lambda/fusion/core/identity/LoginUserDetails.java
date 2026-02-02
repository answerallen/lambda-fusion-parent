package com.lambda.fusion.core.identity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.core.FusionConstants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户身份主体（Principal）
 * <p>
 * 表示系统中已认证的用户身份信息，实现了 {@link LoginUser} 接口。
 * </p>
 *
 * <p>
 * 主要职责：
 * </p>
 * <ul>
 * <li>存储用户基本信息（用户名、昵称、密码等）</li>
 * <li>维护用户组织和租户归属关系</li>
 * <li>管理用户角色和权限</li>
 * <li>提供账户状态查询（是否过期、锁定等）</li>
 * </ul>
 */
@SuppressFBWarnings("EI_EXPOSE_REP")
@Data
@EqualsAndHashCode
@Schema(description = "用户身份主体")
public class LoginUserDetails implements LoginUser {

    /**
     * 用户名
     * <p>
     * 用户登录时使用的用户名，通常为邮箱、手机号或自定义用户名。
     * 在系统中应保证唯一性。
     * </p>
     */
    @Schema(description = "用户名", example = "admin")
    private String username;

    /**
     * 用户密码
     * <p>
     * 用户的登录密码，通常经过加密处理存储。
     * 使用 @JsonIgnore 注解防止在 JSON 序列化时泄露密码信息。
     * </p>
     */
    @JsonIgnore
    @Schema(hidden = true)
    private String password;

    /**
     * 用户昵称
     */
    @Schema(description = "用户昵称", example = "管理员")
    private String nickname;

    /**
     * 组织ID
     * <p>
     * 用户所属的组织标识，用于多组织架构系统。
     * </p>
     */
    @Schema(description = "组织ID", example = "ORG001")
    private String orgId;

    /**
     * 租户ID
     * <p>
     * 用户所属的租户标识，用于多租户系统。
     * </p>
     */
    @Schema(description = "租户ID", example = "TENANT001")
    private String tenantId;

    /**
     * 用户角色集合
     * <p>
     * 存储用户拥有的所有角色，如 ROLE_ADMIN、ROLE_USER 等。
     * </p>
     */
    @Schema(description = "用户角色集合", example = "[\"ROLE_ADMIN\", \"ROLE_USER\"]")
    private Set<String> roles;

    /**
     * 账户是否过期
     * <p>
     * 标识用户账户是否已过期。
     * true 表示账户已过期，false 表示账户未过期。
     * 过期的账户无法进行登录操作。
     * </p>
     */
    @Schema(description = "账户是否过期", example = "false")
    private Boolean accountExpired;

    /**
     * 账户是否被锁定
     * <p>
     * 标识用户账户是否被锁定。
     * true 表示账户已锁定，false 表示账户未锁定。
     * 锁定的账户无法进行登录操作，通常因为多次登录失败或安全原因。
     * </p>
     */
    @Schema(description = "账户是否被锁定", example = "false")
    private Boolean accountLocked;

    /**
     * 账户过期时间
     */
    @Schema(description = "账户过期时间")
    private Date expiredTime;

    /**
     * 获取用户认证凭据
     * <p>
     * 返回用户的认证凭据，通常为密码。
     * 使用 @JsonIgnore 注解防止在 JSON 序列化时泄露凭据信息。
     * </p>
     *
     * @return 用户密码作为认证凭据
     */
    @JsonIgnore
    @Override
    public String getCredentials() {
        return password;
    }

    /**
     * 获取用户名称标识
     * <p>
     * 返回用户的名称标识，在此实现中返回用户名。
     * 使用 @JsonIgnore 注解防止在 JSON 序列化时重复输出。
     * </p>
     *
     * @return 用户名作为名称标识
     */
    @JsonIgnore
    @Override
    public String getName() {
        return username;
    }

    /**
     * 获取用户角色集合
     * <p>
     * 显式提供 getter 方法以提高 API 清晰度。
     * </p>
     *
     * @return 用户角色集合，如果未设置则返回空集合（非 null）
     */
    public Set<String> getRoles() {
        return roles != null ? roles : Collections.emptySet();
    }

    /**
     * 判断用户是否为开发者角色
     *
     * @return true 表示用户具有开发者角色，false 表示不具有
     */
    @JsonIgnore
    public boolean isDev() {
        return roles != null && roles.contains(FusionConstants.ROLE_DEV);
    }

    /**
     * 判断用户是否为管理员角色
     *
     * @return true 表示用户具有管理员角色，false 表示不具有
     */
    @JsonIgnore
    public boolean isAdmin() {
        return roles != null && roles.contains(FusionConstants.ROLE_ADMIN);
    }

    /**
     * 判断用户是否为管理者角色
     *
     * @return true 表示用户具有管理者角色，false 表示不具有
     */
    @JsonIgnore
    public boolean isManager() {
        return roles != null
                && (roles.contains(FusionConstants.ROLE_MANAGER));
    }

    /**
     * 判断用户是否为租户管理员角色
     *
     * @return true 表示用户具有租户管理员角色，false 表示不具有
     */
    @JsonIgnore
    public boolean isTenantManager() {
        return roles != null && roles.contains(FusionConstants.ROLE_TENANT_MANAGER);
    }

    /**
     * 判断用户是否为租户角色
     *
     * @return true 表示用户具有租户角色，false 表示不具有
     */
    @JsonIgnore
    public boolean isTenant() {
        return roles != null && roles.contains(FusionConstants.ROLE_TENANT);
    }

    /**
     * 判断用户是否为系统管理员角色
     *
     * @return true 表示用户具有系统管理员角色，false 表示不具有
     */
    @JsonIgnore
    public boolean isSystem() {
        return roles != null && roles.contains(FusionConstants.ROLE_SYSTEM);
    }
}
