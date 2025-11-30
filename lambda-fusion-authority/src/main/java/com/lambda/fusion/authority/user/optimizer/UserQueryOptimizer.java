package com.lambda.fusion.authority.user.optimizer;

import static com.lambda.fusion.core.utils.ParameterUtils.fuzzyQuery;

import cn.hutool.json.JSONUtil;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.user.model.UserFieldsEntity;
import com.lambda.fusion.authority.user.model.UserQuery;
import com.lambda.fusion.authority.user.model.UserSearchParams;
import com.lambda.fusion.authority.user.service.UserService;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.identity.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 用户查询优化工具类
 *
 * <p>
 * 提供用户相关的批量查询优化方法，减少N+1查询问题，提升性能。
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserQueryOptimizer {
    private final OrganizationService organizationService;
    private final UserService userService;

    /**
     * 构建查询参数
     *
     * @param userQuery 查询DTO
     * @return 查询参数Bean
     */
    public UserSearchParams getUsersQueryParameters(UserQuery userQuery) {
        UserSearchParams params = new UserSearchParams();
        UserPrincipal userPrincipal = OperatorUtils.getLoginUser(UserPrincipal.class);
        String tenantId = userPrincipal.getTenantId();

        // 处理 username (支持逗号分隔)
        if (StringUtils.isNotBlank(userQuery.getUsername())) {
            String[] split = userQuery.getUsername().split(Constants.DELIMITER);
            params.setUsernames(Arrays.asList(split));
        }

        params.setDev(userPrincipal.isDev());
        params.setAdmin(userPrincipal.isAdmin());
        params.setUid(userPrincipal.getName());

        if (StringUtils.isNotBlank(userQuery.getEmail())) {
            params.setEmail(fuzzyQuery(userQuery.getEmail()));
        }
        if (StringUtils.isNotBlank(userQuery.getNickname())) {
            params.setNickname(fuzzyQuery(userQuery.getNickname()));
        }
        if (StringUtils.isNotBlank(userQuery.getMobile())) {
            params.setMobile(fuzzyQuery(userQuery.getMobile()));
        }
        if (StringUtils.isNotBlank(userPrincipal.getTenantId())) {
            params.setTenantId(tenantId);
        }
        if (StringUtils.isNotBlank(userQuery.getAuthority())) {
            params.setAuthority(userQuery.getAuthority());
        }

        // 处理 personal (JSON 字符串转 List<UserFieldsEntity>)
        if (StringUtils.isNotBlank(userQuery.getPersonal())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tempMap = (Map<String, Object>) JSONUtil.parse(userQuery.getPersonal());
            List<UserFieldsEntity> fields = convertPersonBean(tempMap, userPrincipal.getUsername());
            params.setPersonal(fields);
        }

        if (userQuery.getIsOnline() != null) {
            params.setIsOnline(userQuery.getIsOnline());
        }

        Set<String> orgIds = getOrganizationIds(
                userQuery.getOrganizationId(),
                userQuery.getIncludeSubordinates(),
                userQuery.getEnableDataPermission(),
                userPrincipal);
        params.setOrgIds(orgIds);

        return params;
    }

    /**
     * 获取组织机构ID集合
     */
    private Set<String> getOrganizationIds(
            String organizationId, boolean subordinate, boolean dataRight, UserPrincipal userPrincipal) {
        Set<String> orgIds = Sets.newHashSet();
        if (subordinate || StringUtils.isBlank(organizationId)) {
            if (!dataRight) {
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

    /**
     * 用户扩展信息map 转 用户新增字段信息map
     */
    private List<UserFieldsEntity> convertPersonBean(Map<String, Object> personal, String username) {
        List<UserFieldsEntity> userFieldDOS = new ArrayList<>(personal.size());
        personal.forEach((k, v) -> {
            UserFieldsEntity info = new UserFieldsEntity();
            info.setUsername(username);
            info.setFieldName(k);
            info.setFieldValue(v != null ? String.valueOf(v) : null);
            userFieldDOS.add(info);
        });
        return userFieldDOS;
    }
}
