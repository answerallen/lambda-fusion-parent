package com.lambda.fusion.authority.authorize.model.vo;

import com.lambda.fusion.core.user.User;
import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class SimpleUserVO {

    private String username;
    private String nickname;
    private String password;
    private String orgId;
    private String tenantId;
    private Boolean enabled;
    private Date expiredTime;
    private List<String> authorities;

    public User toUser() {
        User user = new User();
        user.setId(username);
        user.setUsername(username);
        user.setPassword(password);
        user.setOrgId(orgId);
        user.setTenantId(tenantId);
        user.setAccountExpired(enabled);
        user.setAccountLocked(enabled);
        user.setDev(enabled);
        return user;
    }
}
