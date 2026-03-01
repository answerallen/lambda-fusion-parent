package com.lambda.fusion.authority.helper;

import static com.lambda.fusion.core.utils.SqlParamUtils.fuzzyQuery;

import cn.hutool.json.JSONUtil;
import com.google.common.collect.Sets;
import com.lambda.fusion.authority.domain.user.UserFieldsEntity;
import com.lambda.fusion.authority.domain.user.UserQuery;
import com.lambda.fusion.authority.domain.user.UserQueryContext;
import com.lambda.fusion.authority.service.OrganizationService;
import com.lambda.fusion.authority.service.UserService;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.LoginUserDetails;
import com.lambda.fusion.core.utils.SecurityUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

@SuppressFBWarnings("EI_EXPOSE_REP2")
@Slf4j
@Component
@RequiredArgsConstructor
public class UserQueryHelper {

    private final OrganizationService organizationService;
    private final UserService userService;

    public UserQueryContext buildUserQueryContext(UserQuery userQuery) {
        UserQueryContext userQueryContext = new UserQueryContext();
        LoginUserDetails loginUserDetails = SecurityUtils.getUser();
        String tenantId = loginUserDetails.getTenantId();

        // 处理 username (支持逗号分隔)
        if (StringUtils.isNotBlank(userQuery.getUsername())) {
            String[] split = userQuery.getUsername().split(FusionConstants.DELIMITER);
            userQueryContext.setUsernames(Arrays.asList(split));
        }

        userQueryContext.setDev(loginUserDetails.isDev());
        userQueryContext.setAdmin(loginUserDetails.isAdmin());
        userQueryContext.setUsername(loginUserDetails.getName());

        if (StringUtils.isNotBlank(userQuery.getEmail())) {
            userQueryContext.setEmail(fuzzyQuery(userQuery.getEmail()));
        }
        if (StringUtils.isNotBlank(userQuery.getNickname())) {
            userQueryContext.setNickname(fuzzyQuery(userQuery.getNickname()));
        }
        if (StringUtils.isNotBlank(userQuery.getMobile())) {
            userQueryContext.setMobile(fuzzyQuery(userQuery.getMobile()));
        }
        if (StringUtils.isNotBlank(loginUserDetails.getTenantId())) {
            userQueryContext.setTenantId(tenantId);
        }
        if (StringUtils.isNotBlank(userQuery.getAuthority())) {
            userQueryContext.setAuthority(userQuery.getAuthority());
        }

        if (StringUtils.isNotBlank(userQuery.getPersonal())) {
            Map<String, Object> tempMap = JSONUtil.parseObj(userQuery.getPersonal());
            List<UserFieldsEntity> fields =
                    UserInfoHelper.buildUserFieldsFromMap(tempMap, loginUserDetails.getUsername());
            userQueryContext.setUserFields(fields);
        }

        if (userQuery.getIsOnline() != null) {
            userQueryContext.setIsOnline(userQuery.getIsOnline());
        }

        Set<String> orgIds = resolveOrganizationIds(
                userQuery.getOrganizationId(),
                userQuery.getIncludeChildren(),
                userQuery.getEnableDataPermission(),
                loginUserDetails);
        userQueryContext.setOrgIds(orgIds);

        return userQueryContext;
    }

    private Set<String> resolveOrganizationIds(
            String organizationId, boolean includeChild, boolean dataPermission, LoginUserDetails loginUserDetails) {
        Set<String> orgIds = Sets.newHashSet();
        if (includeChild || StringUtils.isBlank(organizationId)) {
            if (!dataPermission) {
                if (StringUtils.isNotBlank(organizationId)) {
                    List<String> subOrgIds = organizationService.getChildrenById(organizationId);
                    orgIds.addAll(subOrgIds);
                }
            } else {
                orgIds.addAll(userService.getSubOrganizationIds(organizationId, loginUserDetails));
            }
        } else {
            orgIds.add(organizationId);
        }
        return orgIds;
    }
}
