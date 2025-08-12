package com.lambda.fusion.core.user;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.security.SimpleLoginUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class User extends SimpleLoginUser {
    private String orgId;
    private String tenantId;
    private String nickname;

    @JsonIgnore
    public Boolean isDev() {
        return CollUtil.contains(getRoles(), "ROLE_DEV");
    }

    @JsonIgnore
    public Boolean isAdmin() {
        return CollUtil.contains(getRoles(), "ROLE_ADMIN");
    }

    @JsonIgnore
    public Boolean isManager() {
        return CollUtil.contains(getRoles(), "ROLE_MANAGER");
    }

    @JsonIgnore
    public Boolean isTenantManager() {
        return CollUtil.contains(getRoles(), "ROLE_TENANT_MANAGER");
    }
}
