package com.lambda.fusion.authority.user.helper;

import com.lambda.fusion.authority.role.model.SimpleRole;
import com.lambda.fusion.authority.user.model.User;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.LoginUserDetails;
import com.lambda.fusion.core.utils.LoginUserUtils;
import org.apache.commons.lang.StringUtils;

public class UserPermissionHelper {

    public static boolean isSelf(User user) {
        LoginUserDetails loginUser = LoginUserUtils.getLoginUser();
        return loginUser.getUsername().equals(user.getUsername());
    }

    public static boolean isTenant(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_TENANT));
    }

    public static boolean isTenantManager(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_TENANT_MANAGER));
    }

    public static boolean isAdmin(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_ADMIN));
    }

    public static boolean isDev(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_USER));
    }
}
