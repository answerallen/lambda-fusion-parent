package com.lambda.fusion.authority.authentication.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.authority.authentication.mapper.AuthenticationMapper;
import com.lambda.fusion.authority.authentication.model.AuthenticatedUser;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.authentication.model.UserDetails;
import com.lambda.fusion.authority.authentication.service.AuthenticationService;
import com.lambda.fusion.authority.resource.model.ResourceTree;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.model.UserInfoEntity;
import com.lambda.fusion.authority.user.model.UserProfile;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.LoginUserDetails;
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
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationMapper authenticationMapper;
    private final UserInfoMapper userInfoMapper;

    @Override
    public LoginUser loginByUsername(String username, String loginType) {
        Assert.notNull(username, "parameter 'username' cannot be empty or null");
        UserDetails authUserDetails = authenticationMapper.selectUserDetailByUsername(username);
        if (authUserDetails == null) {
            throw new UsernameNotFoundException("user in not found");
        }
        LoginUserDetails loginUserDetails = authUserDetails.toUserPrincipal();
        return prepareLoginUser(loginUserDetails);
    }

    @Override
    public LoginUser loginByMobile(String mobile, String loginType) throws AuthenticationException {
        List<UserDetails> details = authenticationMapper.selectUserDetailsByMobile(mobile);
        UserDetails user = Optional.ofNullable(details)
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
        if (loginUser instanceof LoginUserDetails && CollUtil.isNotEmpty(((LoginUserDetails) loginUser).getRoles())) {
            query.setIds(new ArrayList<>(((LoginUserDetails) loginUser).getRoles()));
        }
        return getNavigation(loginUser, query);
    }

    @Override
    public List<ResourceTree> getNavigation(LoginUser operator, NavigationQuery query) {
        Assert.notNull(query, "parameter 'query' cannot be empty or null");
        List<ResourceTree> resourceTrees = authenticationMapper.selectNavigation(query);
        return TreeBuilder.build(resourceTrees);
    }

    @Override
    public List<UserProfile> getUsersByRoleId(String roleId) {
        return authenticationMapper.selectUserProfileByRoleId(roleId);
    }

    @Override
    public AuthenticatedUser getUserInfo() {
        LoginUser operator = OperatorUtils.getOperator();
        UserDetails userDetails = authenticationMapper.selectUserDetailByUsername(operator.getName());
        if (userDetails == null) {
            throw new UsernameNotFoundException("user not found");
        }

        AuthenticatedUser user = new AuthenticatedUser();
        BeanUtil.copyProperties(userDetails, user);

        user.setUserId(userDetails.getUsername());
        user.setRealName(userDetails.getNickname());

        if (CollUtil.isNotEmpty(userDetails.getAuthorities())) {
            user.setRoles(new ArrayList<>(userDetails.getAuthorities()));
        } else {
            user.setRoles(new ArrayList<>());
        }

        UserInfoEntity userInfoEntity = userInfoMapper.selectById(operator.getName());
        if (userInfoEntity != null) {
            user.setAvatar(userInfoEntity.getAvatar());
            user.setDesc(userInfoEntity.getRemark());
        }

        user.setToken(StpUtil.getTokenValue());
        user.setHomePath("/dashboard/analysis");

        return user;
    }

    @Override
    public LoginUser loadByThirdLoginResult(ThirdPartLoginResult thirdLoginResult, String loginType) {
        return null;
    }

    @Override
    public List<String> getAuthorities() {
        LoginUser operator = OperatorUtils.getOperator();
        return authenticationMapper.selectAuthoritiesByUsername(operator.getName());
    }

    /**
     * 构建登录用户信息
     *
     * @param loginUserDetails 用户对象
     * @return 登录用户
     */
    private LoginUser prepareLoginUser(LoginUserDetails loginUserDetails) {
        if (CollUtil.isEmpty(loginUserDetails.getRoles())) {
            loginUserDetails.setRoles(Sets.newHashSet(FusionConstants.ROLE_USER));
        }
        String tenantId = TenantHolder.getTenantId();
        if (StrUtil.isNotBlank(tenantId)) {
            loginUserDetails.setTenantId(tenantId);
        }
        return loginUserDetails;
    }
}
