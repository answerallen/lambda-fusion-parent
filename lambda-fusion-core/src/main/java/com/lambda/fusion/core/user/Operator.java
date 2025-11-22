package com.lambda.fusion.core.user;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.cloud.core.principal.LoginUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class Operator implements LoginUser {

    /**
     * 用户名
     * <p>
     * 用户登录时使用的用户名，通常为邮箱、手机号或自定义用户名。
     * 在系统中应保证唯一性。
     * </p>
     */
    private String username;

    /**
     * 用户密码
     * <p>
     * 用户的登录密码，通常经过加密处理存储。
     * 使用@JsonIgnore注解防止在JSON序列化时泄露密码信息。
     * </p>
     */
    @JsonIgnore
    private String password;

    /**
     * 组织ID
     * <p>
     * 用户所属的组织标识，用于多组织架构系统。
     * 在简单实现中返回空字符串，实际应用中可根据需要返回具体的组织ID。
     * </p>
     */
    private String orgId;

    /**
     * 租户ID
     * <p>
     * 用户所属的租户标识，用于多租户系统。
     * 在简单实现中返回空字符串，实际应用中可根据需要返回具体的租户ID。
     * </p>
     */
    private String tenantId;

    /**
     * 账户是否过期
     * <p>
     * 标识用户账户是否已过期。
     * true表示账户已过期，false表示账户未过期。
     * 过期的账户无法进行登录操作。
     * </p>
     */
    private Boolean accountExpired;

    /**
     * 账户是否被锁定
     * <p>
     * 标识用户账户是否被锁定。
     * true表示账户已锁定，false表示账户未锁定。
     * 锁定的账户无法进行登录操作，通常因为多次登录失败或安全原因。
     * </p>
     */
    private Boolean accountLocked;

    /**
     * 获取用户认证凭据
     * <p>
     * 返回用户的认证凭据，通常为密码。
     * 使用@JsonIgnore注解防止在JSON序列化时泄露凭据信息。
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
     * 返回用户的名称标识，在此实现中返回用户ID。
     * 使用@JsonIgnore注解防止在JSON序列化时重复输出。
     * </p>
     *
     * @return 用户ID作为名称标识
     */
    @JsonIgnore
    @Override
    public String getName() {
        return username;
    }

    private String nickname;

    private Set<String> roles;

    @Schema(description = "过期时间")
    private Date expiredTime;

    @JsonIgnore
    public Boolean isDev() {
        return CollUtil.contains(roles, "ROLE_DEV");
    }

    @JsonIgnore
    public Boolean isAdmin() {
        return CollUtil.contains(roles, "ROLE_ADMIN");
    }

    @JsonIgnore
    public Boolean isManager() {
        return CollUtil.contains(roles, "ROLE_MANAGER");
    }

    @JsonIgnore
    public Boolean isTenantManager() {
        return CollUtil.contains(roles, "ROLE_TENANT_MANAGER");
    }
}
