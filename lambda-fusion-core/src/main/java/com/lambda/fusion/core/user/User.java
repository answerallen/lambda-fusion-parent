package com.lambda.fusion.core.user;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.security.SimpleLoginUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class User extends SimpleLoginUser {

    private String nickname;

    private Set<String> roles;

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
