package com.lambda.fusion.auth.login.domain;


import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.core.base.user.LoginUserDetails;
import lombok.Data;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Date;


@Data
public class UserDTO {

    private String username;
    private String nickname;
    private String password;
    private String orgId;
    private String tenantId;
    private Boolean enabled;
    private Date expiredTime;


    public LoginUser toUser() {
        return new LoginUserDetails();
    }

}
