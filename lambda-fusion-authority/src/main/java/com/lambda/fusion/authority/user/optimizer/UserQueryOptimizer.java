package com.lambda.fusion.authority.user.optimizer;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.user.model.dto.UserPageQueryDTO;
import com.lambda.fusion.authority.user.service.UserService;
import com.lambda.fusion.core.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.lambda.fusion.core.utils.ParameterUtils.fuzzyQuery;

/**
 * 用户查询优化工具类
 *
 * <p>提供用户相关的批量查询优化方法，减少N+1查询问题，提升性能。
 *
 * @author Lambda Fusion Team
 * @since 1.0.0
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
     * @return 查询参数Map
     */
    public Map<String, Object> getMutableUsersQueryParameters(UserPageQueryDTO queryDTO) {
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(11);
        User operator = OperatorUtils.getLoginUser(User.class);
        String tenantId = operator.getTenantId();

        parameters.put("username", queryDTO.getUsername());
        parameters.put("dev", operator.isDev());
        parameters.put("admin", operator.isAdmin());
        parameters.put("uid", operator.getName());

        if (StringUtils.isNotBlank(queryDTO.getEmail())) {
            parameters.put("email", fuzzyQuery(queryDTO.getEmail()));
        }
        if (StringUtils.isNotBlank(queryDTO.getNickname())) {
            parameters.put("nickname", fuzzyQuery(queryDTO.getNickname()));
        }
        if (StringUtils.isNotBlank(queryDTO.getMobile())) {
            parameters.put("mobile", fuzzyQuery(queryDTO.getMobile()));
        }
        if (StringUtils.isNotBlank(operator.getTenantId())) {
            parameters.put("tenant_id", tenantId);
        }
        if (StringUtils.isNotBlank(queryDTO.getAuthority())) {
            parameters.put("authority", queryDTO.getAuthority());
        }
        if (StringUtils.isNotBlank(queryDTO.getPersonal())) {
            parameters.put("personal", queryDTO.getPersonal());
        }
        if (queryDTO.getIsOnline() != null) {
            parameters.put("isOnline", queryDTO.getIsOnline());
        }

        addOrganizationParameter(
                queryDTO.getOrganizationId(),
                queryDTO.getSubordinate() != null ? queryDTO.getSubordinate() : true,
                parameters,
                queryDTO.getDataRight() != null ? queryDTO.getDataRight() : true, operator);

        return parameters;
    }

    /**
     * 增加组织机构参数
     *
     * @param organizationId 组织id
     * @param subordinate    是否包含下级机构
     * @param parameters     参数信息
     * @param dataRight      是否包含数据权限
     */
    private void addOrganizationParameter(
            String organizationId, boolean subordinate, Map<String, Object> parameters, boolean dataRight, User operator) {
        if (subordinate || StringUtils.isBlank(organizationId)) {
            if (!dataRight) {
                if (StringUtils.isNotBlank(organizationId)) {
                    List<String> subOrgIds = organizationService.getChildrenById(organizationId);
                    parameters.put("orgIds", Sets.newHashSet(subOrgIds));
                }
            } else {
                parameters.put("orgIds", userService.getSubOrgIds(organizationId, operator));
            }
        } else {
            List<String> list = new ArrayList<>();
            list.add(organizationId);
            parameters.put("orgIds", list);
        }
    }
}
