package com.lambda.fusion.authority.authentication.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.authority.authentication.mapper.AuthenticationMapper;
import com.lambda.fusion.authority.authentication.model.AuthUserDetails;
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
import java.util.Optional;

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
        AuthUserDetails authUserDetails = authenticationMapper.selectUserDetailByUsername(username);
        if (authUserDetails == null) {
            throw new UsernameNotFoundException("user in not found");
        }
        UserPrincipal userPrincipal = authUserDetails.toUserPrincipal();
        return prepareLoginUser(userPrincipal);
    }

    @Override
    public LoginUser loginByMobile(String mobile, String loginType) throws AuthenticationException {
        List<AuthUserDetails> details = authenticationMapper.selectUserDetailsByMobile(mobile);
        AuthUserDetails user = Optional.ofNullable(details)
                .filter(CollUtil::isNotEmpty)
                .filter(d -> d.size() == 1)
                .map(List::getFirst)
                .orElseThrow(() -> new UsernameNotFoundException("Mobile not found"));
        return prepareLoginUser(user.toUserPrincipal());
    }

    @Override
    public List<ResourceTree> getNavigation(LoginUser loginUser, String parentId, Integer level) {
        NavigationQuery query = new NavigationQuery();
        query.setParentId(parentId);
        query.setLevel(level);
        query.setMode(0);
        if (loginUser instanceof UserPrincipal && CollUtil.isNotEmpty(((UserPrincipal) loginUser).getRoles())) {
            query.setIds(new ArrayList<>(((UserPrincipal) loginUser).getRoles()));
        }
        return getNavigation(loginUser, query);
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
        return null;
    }

    /**
     * 构建登录用户信息
     *
     * @param userPrincipal 用户对象
     * @return 登录用户
     */
    private LoginUser prepareLoginUser(UserPrincipal userPrincipal) {
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
