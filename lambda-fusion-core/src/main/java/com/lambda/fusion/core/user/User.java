package com.lambda.fusion.core.user;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.security.SimpleLoginUser;
import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class User extends SimpleLoginUser {

    private String nickname;

    private Set<String> roles;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 获取用户角色集合
     *
     * @return 角色集合的防御性副本
     */
    public Set<String> getRoles() {
        return roles == null ? null : new HashSet<>(roles);
    }

    /**
     * 设置用户角色集合
     *
     * @param roles 角色集合
     */
    public void setRoles(Set<String> roles) {
        this.roles = roles == null ? null : new HashSet<>(roles);
    }

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
