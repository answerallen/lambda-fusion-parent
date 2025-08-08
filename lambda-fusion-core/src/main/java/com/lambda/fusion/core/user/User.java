package com.lambda.fusion.core.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.security.SimpleLoginUser;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User extends SimpleLoginUser {
    private String orgId;
    private String tenantId;
    private Boolean dev;

    @JsonIgnore
    public Boolean isDev() {
        return dev;
    }
}
