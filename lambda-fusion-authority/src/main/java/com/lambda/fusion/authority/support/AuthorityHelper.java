package com.lambda.fusion.authority.support;

import static com.lambda.fusion.core.FusionConstants.*;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.lambda.fusion.authority.model.role.SimpleRole;
import com.lambda.fusion.authority.model.user.User;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.core.utils.AuthUtils;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class AuthorityHelper {

    public static boolean isSelf(User user) {
        UserDetails loginUser = AuthUtils.getUser();
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
                .anyMatch(role -> role.contains(ROLE_ADMIN));
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
                .anyMatch(role -> role.contains(ROLE_DEV));
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
        return roles != null && roles.stream().map(SimpleRole::getAuthority).anyMatch(role -> role.contains(ROLE_DEV));
    }

    public static boolean isSystem(Collection<SimpleRole> roles) {
        return roles != null
                && roles.stream()
                        .map(SimpleRole::getAuthority)
                        .anyMatch(role -> role.contains(FusionConstants.ROLE_SYSTEM));
    }

    public static boolean isAdmin(Collection<SimpleRole> roles) {
        return roles != null
                && roles.stream().map(SimpleRole::getAuthority).anyMatch(role -> role.contains(ROLE_ADMIN));
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

    /**
     * 是否包含任意一种管理角色(ROLE_DEV,ROLE_ADMIN,ROLE_TENANT)
     */
    public static boolean containsAnyManager(@NotNull User operator) {
        if (CollectionUtils.isNotEmpty(operator.getAuthorities())) {
            return operator.getAuthorities().stream().anyMatch(simpleRole -> {
                String authority = simpleRole.getAuthority();
                return authority.equals(ROLE_DEV)
                        || authority.equals(ROLE_ADMIN)
                        || authority.startsWith(ROLE_TENANT + AT);
            });
        }
        return false;
    }

    public static boolean isTenantAdminRole(String authority) {
        return ROLE_TENANT.equals(authority) || authority.startsWith(ROLE_TENANT + AT);
    }

    public static boolean hasTenantAdminRole(User user) {
        List<SimpleRole> roles = user == null ? null : user.getAuthorities();
        return roles != null
                && !roles.isEmpty()
                && roles.stream().map(SimpleRole::getAuthority).anyMatch(AuthorityHelper::isTenantAdminRole);
    }
}
