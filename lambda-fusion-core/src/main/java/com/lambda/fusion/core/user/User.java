package com.lambda.fusion.core.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.security.SimpleLoginUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class User extends SimpleLoginUser {
    private String orgId;
    private String tenantId;
    private Boolean dev;
    private String nickname;

    @JsonIgnore
    public Boolean isDev() {
        return dev;
    }
}
