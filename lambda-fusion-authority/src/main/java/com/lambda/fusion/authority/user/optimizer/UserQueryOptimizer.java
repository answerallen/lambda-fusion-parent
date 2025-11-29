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
     * @param queryDTO 查询DTO
     * @return 查询参数Bean
     */
    public UserSearchParams getUsersQueryParameters(UserQuery queryDTO) {
        UserSearchParams params = new UserSearchParams();
        UserPrincipal userPrincipal = OperatorUtils.getLoginUser(UserPrincipal.class);
        String tenantId = userPrincipal.getTenantId();

        // 处理 username (支持逗号分隔)
        if (StringUtils.isNotBlank(queryDTO.getUsername())) {
            String[] split = queryDTO.getUsername().split(Constants.DELIMITER);
            params.setUsernames(Arrays.asList(split));
        }

        params.setDev(userPrincipal.isDev());
        params.setAdmin(userPrincipal.isAdmin());
        params.setUid(userPrincipal.getName());

        if (StringUtils.isNotBlank(queryDTO.getEmail())) {
            params.setEmail(fuzzyQuery(queryDTO.getEmail()));
        }
        if (StringUtils.isNotBlank(queryDTO.getNickname())) {
            params.setNickname(fuzzyQuery(queryDTO.getNickname()));
        }
        if (StringUtils.isNotBlank(queryDTO.getMobile())) {
            params.setMobile(fuzzyQuery(queryDTO.getMobile()));
        }
        if (StringUtils.isNotBlank(userPrincipal.getTenantId())) {
            params.setTenantId(tenantId);
        }
        if (StringUtils.isNotBlank(queryDTO.getAuthority())) {
            params.setAuthority(queryDTO.getAuthority());
        }

        // 处理 personal (JSON 字符串转 List<UserFieldsEntity>)
        if (StringUtils.isNotBlank(queryDTO.getPersonal())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tempMap = (Map<String, Object>) JSONUtil.parse(queryDTO.getPersonal());
            List<UserFieldsEntity> fields = convertPersonBean(tempMap, null);
            params.setPersonal(fields);
        }

        if (queryDTO.getIsOnline() != null) {
            params.setIsOnline(queryDTO.getIsOnline());
        }

        Set<String> orgIds = getOrganizationIds(
                queryDTO.getOrganizationId(),
                queryDTO.getSubordinate() != null ? queryDTO.getSubordinate() : true,
                queryDTO.getDataRight() != null ? queryDTO.getDataRight() : true,
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
                orgIds.addAll(userService.getSubOrgIds(organizationId, userPrincipal));
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
