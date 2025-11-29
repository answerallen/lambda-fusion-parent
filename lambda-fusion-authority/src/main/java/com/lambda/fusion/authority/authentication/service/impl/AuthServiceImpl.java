package com.lambda.fusion.authority.authentication.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.authority.authentication.mapper.AuthenticationMapper;
import com.lambda.fusion.authority.authentication.model.AuthUserDetail;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.authentication.service.AuthService;
import com.lambda.fusion.authority.resource.model.ResourceTree;
import com.lambda.fusion.authority.user.model.UserProfile;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.identity.UserPrincipal;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.security.exception.AuthenticationException;
import com.lambda.security.exception.UsernameNotFoundException;
import com.lambda.security.provider.ThirdPartLoginResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现类
 * 负责用户认证、授权和导航菜单相关的业务逻辑实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationMapper authenticationMapper;

    @Override
    public LoginUser loginByUsername(String username, String loginType) {
        Assert.notNull(username, "parameter 'username' cannot be empty or null");
        AuthUserDetail authUserDetail = authenticationMapper.selectUserDetailByUsername(username);
        if (authUserDetail == null) {
            throw new UsernameNotFoundException("user in not found");
        }
        UserPrincipal userPrincipal = authUserDetail.toUserPrincipal();
        return buildLoginUser(userPrincipal);
    }

    @Override
    public LoginUser loginByMobile(String mobile, String loginType) throws AuthenticationException {
        List<AuthUserDetail> authUserDetails = authenticationMapper.selectUserDetailsByMobile(mobile);
        if (CollUtil.isEmpty(authUserDetails)) {
            throw new UsernameNotFoundException("mobile in not found");
        }
        if (authUserDetails.size() > 1) {
            throw new AuthenticationException("mobile in not unique");
        }
        AuthUserDetail authUserDetail = authUserDetails.getFirst();
        return buildLoginUser(authUserDetail.toUserPrincipal());
    }

    @Override
    public List<ResourceTree> getNavigation(LoginUser operator, String parentId, Integer level) {
        NavigationQuery query = new NavigationQuery();
        query.setParentId(parentId);
        query.setLevel(level);
        query.setMode(0);
        if (operator instanceof UserPrincipal && CollUtil.isNotEmpty(((UserPrincipal) operator).getRoles())) {
            query.setIds(new ArrayList<>(((UserPrincipal) operator).getRoles()));
        }
        return getNavigation(operator, query);
    }

    @Override
    public List<ResourceTree> getNavigation(LoginUser operator, NavigationQuery query) {
        Assert.notNull(operator, "parameter 'operator' cannot be empty or null");
        Assert.notNull(query, "parameter 'query' cannot be empty or null");
        List<ResourceTree> resourceTrees = authenticationMapper.selectNavigation(query);
        return TreeBuilder.build(resourceTrees);
    }

    @Override
    public List<UserProfile> getUsersByRoleId(String roleId) {
        return authenticationMapper.selectUserProfileByRoleId(roleId);
    }

    @Override
    public LoginUser loadByThirdLoginResult(ThirdPartLoginResult thirdLoginResult, String loginType) {
        // TODO: 实现第三方登录逻辑
        return null;
    }

    /**
     * 构建登录用户信息
     *
     * @param userPrincipal 用户对象
     * @return 登录用户
     */
    private LoginUser buildLoginUser(UserPrincipal userPrincipal) {
        if (CollUtil.isEmpty(userPrincipal.getRoles())) {
            userPrincipal.setRoles(Sets.newHashSet(Constants.ROLE_USER));
        }
        String tenantId = TenantHolder.getTenantId();
        if (StrUtil.isNotBlank(tenantId)) {
            userPrincipal.setTenantId(tenantId);
        }
        return userPrincipal;
    }
}
