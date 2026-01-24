package com.lambda.fusion.authority.user.helper;

import static com.lambda.fusion.core.utils.SqlParamUtils.fuzzyQuery;

import cn.hutool.json.JSONUtil;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.user.model.UserFieldsEntity;
import com.lambda.fusion.authority.user.model.UserQuery;
import com.lambda.fusion.authority.user.model.UserQueryContext;
import com.lambda.fusion.authority.user.service.UserService;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserPrincipal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserQueryHelper {

    private final OrganizationService organizationService;
    private final UserService userService;

    public UserQueryContext buildUserQueryContext(UserQuery userQuery) {
        UserQueryContext userQueryContext = new UserQueryContext();
        UserPrincipal userPrincipal = OperatorUtils.getLoginUser(UserPrincipal.class);
        String tenantId = userPrincipal.getTenantId();

        // 处理 username (支持逗号分隔)
        if (StringUtils.isNotBlank(userQuery.getUsername())) {
            String[] split = userQuery.getUsername().split(FusionConstants.DELIMITER);
            userQueryContext.setUsernames(Arrays.asList(split));
        }

        userQueryContext.setDev(userPrincipal.isDev());
        userQueryContext.setAdmin(userPrincipal.isAdmin());
        userQueryContext.setUsername(userPrincipal.getName());

        if (StringUtils.isNotBlank(userQuery.getEmail())) {
            userQueryContext.setEmail(fuzzyQuery(userQuery.getEmail()));
        }
        if (StringUtils.isNotBlank(userQuery.getNickname())) {
            userQueryContext.setNickname(fuzzyQuery(userQuery.getNickname()));
        }
        if (StringUtils.isNotBlank(userQuery.getMobile())) {
            userQueryContext.setMobile(fuzzyQuery(userQuery.getMobile()));
        }
        if (StringUtils.isNotBlank(userPrincipal.getTenantId())) {
            userQueryContext.setTenantId(tenantId);
        }
        if (StringUtils.isNotBlank(userQuery.getAuthority())) {
            userQueryContext.setAuthority(userQuery.getAuthority());
        }

        if (StringUtils.isNotBlank(userQuery.getPersonal())) {
            Map<String, Object> tempMap = JSONUtil.parseObj(userQuery.getPersonal());
            List<UserFieldsEntity> fields = buildUserFieldsFromMap(tempMap, userPrincipal.getUsername());
            userQueryContext.setUserFields(fields);
        }

        if (userQuery.getIsOnline() != null) {
            userQueryContext.setIsOnline(userQuery.getIsOnline());
        }

        Set<String> orgIds = resolveOrganizationIds(
                userQuery.getOrganizationId(),
                userQuery.getIncludeChild(),
                userQuery.getEnableDataPermission(),
                userPrincipal);
        userQueryContext.setOrgIds(orgIds);

        return userQueryContext;
    }

    public Set<String> resolveOrganizationIds(
            String organizationId, boolean includeChild, boolean dataPermission, UserPrincipal userPrincipal) {
        Set<String> orgIds = Sets.newHashSet();
        if (includeChild || StringUtils.isBlank(organizationId)) {
            if (!dataPermission) {
                if (StringUtils.isNotBlank(organizationId)) {
                    List<String> subOrgIds = organizationService.getChildrenById(organizationId);
                    orgIds.addAll(subOrgIds);
                }
            } else {
                orgIds.addAll(userService.getSubOrganizationIds(organizationId, userPrincipal));
            }
        } else {
            orgIds.add(organizationId);
        }
        return orgIds;
    }

    public List<UserFieldsEntity> buildUserFieldsFromMap(Map<String, Object> personal, String username) {
        return getUserFieldsEntities(personal, username);
    }

    public List<UserFieldsEntity> getUserFieldsEntities(Map<String, Object> personal, String username) {
        List<UserFieldsEntity> userFields = new ArrayList<>(personal.size());
        personal.forEach((k, v) -> {
            UserFieldsEntity info = new UserFieldsEntity();
            info.setUsername(username);
            info.setFieldName(k);
            info.setFieldValue(v != null ? String.valueOf(v) : null);
            userFields.add(info);
        });
        return userFields;
    }
}
