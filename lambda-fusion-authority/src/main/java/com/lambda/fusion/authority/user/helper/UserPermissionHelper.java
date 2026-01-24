package com.lambda.fusion.authority.user.helper;

import com.lambda.fusion.authority.role.model.SimpleRole;
import com.lambda.fusion.authority.user.model.User;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserPrincipal;
import com.lambda.fusion.core.utils.LoginUserUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserPermissionHelper {

    public boolean isSelf(User user) {
        UserPrincipal loginUser = LoginUserUtils.getLoginUser();
        return loginUser.getUsername().equals(user.getUsername());
    }


    public boolean isTenant(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_TENANT));
    }


    public boolean isTenantManager(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_TENANT_MANAGER));
    }


    public boolean isAdmin(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_ADMIN));
    }


    public boolean isDev(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_USER));
    }
}
