package com.lambda.fusion.authority.helper;

import static com.lambda.fusion.core.FusionConstants.AT;

import com.lambda.fusion.authority.model.role.SimpleRole;
import com.lambda.fusion.authority.model.user.User;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.core.utils.SecurityUtils;
import java.util.*;
import org.apache.commons.lang.StringUtils;

public class UserRoleHelper {

    public static boolean isSelf(User user) {
        UserDetails loginUser = SecurityUtils.getUser();
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

    public static boolean isSystem(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_SYSTEM));
    }

    public static boolean isDev(User user) {
        return user.getAuthorities().stream()
                .map(SimpleRole::getAuthority)
                .filter(StringUtils::isNotBlank)
                .anyMatch(role -> role.contains(FusionConstants.ROLE_USER));
    }

    public static boolean isTenant(Collection<SimpleRole> roles) {
        return roles != null
                && roles.stream()
                        .map(SimpleRole::getAuthority)
                        .anyMatch(role -> role.startsWith(FusionConstants.ROLE_TENANT));
    }

    public static boolean isTenantManager(Collection<SimpleRole> roles) {
        return roles != null
                && roles.stream()
                        .map(SimpleRole::getAuthority)
                        .anyMatch(role -> role.contains(FusionConstants.ROLE_TENANT_MANAGER));
    }

    public static boolean isDev(Collection<SimpleRole> roles) {
        return roles != null
                && roles.stream()
                        .map(SimpleRole::getAuthority)
                        .anyMatch(role -> role.contains(FusionConstants.ROLE_DEV));
    }

    public static boolean isSystem(Collection<SimpleRole> roles) {
        return roles != null
                && roles.stream()
                        .map(SimpleRole::getAuthority)
                        .anyMatch(role -> role.contains(FusionConstants.ROLE_SYSTEM));
    }

    public static boolean isAdmin(Collection<SimpleRole> roles) {
        return roles != null
                && roles.stream()
                        .map(SimpleRole::getAuthority)
                        .anyMatch(role -> role.contains(FusionConstants.ROLE_ADMIN));
    }

    public static boolean isManager(Collection<SimpleRole> roles) {
        return roles != null
                && roles.stream()
                        .map(SimpleRole::getAuthority)
                        .anyMatch(role -> role.contains(FusionConstants.ROLE_MANAGER));
    }

    public static String getTenantId(String authority) {
        if (StringUtils.isNotBlank(authority) && authority.contains(AT)) {
            return authority.substring(authority.indexOf(AT) + 1);
        }
        return null;
    }
}
