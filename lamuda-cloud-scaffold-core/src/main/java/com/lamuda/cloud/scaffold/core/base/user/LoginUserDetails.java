package com.lamuda.cloud.scaffold.core.base.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lamuda.cloud.core.principal.LoginUser;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginUserDetails implements LoginUser {

    private String id;
    @JsonIgnore
    private String username;
    private String password;
    private String orgId;
    private String tenantId;
    private Boolean accountLocked;
    private Boolean accountExpired;
    private Boolean dev;

    @Override
    public String getUsername() {
        return username;
    }

    @JsonIgnore
    @Override
    public String getCredentials() {
        return password;
    }

    @JsonIgnore
    @Override
    public Boolean getAccountLocked() {
        return accountLocked;
    }

    @JsonIgnore
    @Override
    public Boolean getAccountExpired() {
        return accountExpired;
    }

    @Override
    public String getName() {
        return username;
    }


    @JsonIgnore
    public Boolean isDev() {
        return dev;
    }
}
