package com.lambda.fusion.core.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.security.SimpleLoginUser;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
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
