package com.lambda.fusion.authority.authentication.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.fusion.authority.authentication.mapper.AuthenticationMapper;
import com.lambda.fusion.authority.authentication.model.*;
import com.lambda.fusion.authority.authentication.service.AuthenticationService;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.model.UserInfoEntity;
import com.lambda.fusion.authority.user.model.UserProfile;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.LoginUserDetails;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.utils.SecurityUtils;
import com.lambda.security.exception.AuthenticationException;
import com.lambda.security.exception.UsernameNotFoundException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
@SuppressFBWarnings("EI_EXPOSE_REP2")
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationMapper authenticationMapper;
    private final ObjectMapper objectMapper;
    private final UserInfoMapper userInfoMapper;

    @Override
    public LoginUser loginByUsername(String username, String loginType) {
        if (username == null) {
            throw AuthorityBusinessException.invalidParameter("username不能为空");
        }
        UserDetails authUserDetails = authenticationMapper.selectUserDetailByUsername(username);
        if (authUserDetails == null) {
            throw AuthorityBusinessException.userNotFound(username);
        }
        LoginUserDetails loginUserDetails = authUserDetails.toLoginUser();
        return prepareLoginUser(loginUserDetails);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>(SecurityUtils.getUser().getRoles());
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return authenticationMapper.selectAuthoritiesByUsername(loginId.toString());
    }

    @Override
    public LoginUser loginByMobile(String mobile, String loginType) throws AuthenticationException {
        List<UserDetails> details = authenticationMapper.selectUserDetailsByMobile(mobile);
        UserDetails user = Optional.ofNullable(details)
                .filter(CollUtil::isNotEmpty)
                .filter(d -> d.size() == 1)
                .map(List::getFirst)
                .orElseThrow(() -> new UsernameNotFoundException("手机号不存在！"));
        return prepareLoginUser(user.toLoginUser());
    }

    @Override
    public List<NavigationRoute> getNavigation(LoginUserDetails loginUser, String parentId, Integer level) {
        NavigationQuery query = new NavigationQuery();
        query.setParentId(parentId);
        query.setLevel(level);
        query.setMode(0);
        if (!loginUser.isDev()) {
            query.setIds(new ArrayList<>(loginUser.getRoles()));
        }
        return getNavigation(loginUser, query);
    }

    @Override
    public List<NavigationRoute> getNavigation(LoginUserDetails loginUserDetails, NavigationQuery query) {
        List<NavigationRoute> navigationRoutes = authenticationMapper.selectNavigation(query);
        List<NavigationRoute> navigationRouteTree = TreeBuilder.build(navigationRoutes);
        enrichNavigationRoutes(navigationRouteTree);
        return navigationRouteTree;
    }

    private void enrichNavigationRoutes(List<NavigationRoute> navigationRouteTree) {
        if (CollUtil.isEmpty(navigationRouteTree)) {
            return;
        }
        for (NavigationRoute root : navigationRouteTree) {
            enrichNavigationNode(root);
        }
    }

    private void enrichNavigationNode(NavigationRoute node) {
        if (node == null) {
            return;
        }

        NavigationRouteMeta meta = node.getMeta();
        if (meta == null) {
            meta = new NavigationRouteMeta();
            node.setMeta(meta);
        }

        meta.putIfAbsent("title", node.getName());
        meta.putIfAbsent("icon", node.getIcon());
        meta.putIfAbsent("order", node.getOrderNo());
        meta.putIfAbsent("hideInMenu", node.isHidden());
        meta.putIfAbsent("keepAlive", node.isKeepAlive());

        if (StrUtil.isBlank(node.getComponent())) {
            String component = resolveComponent(node, meta);
            if (StrUtil.isNotBlank(component)) {
                node.setComponent(component);
            } else {
                node.setComponent(StrUtil.removePrefix(node.getPath(), "/") + "/index");
            }
        }

        if (StrUtil.isBlank(node.getRedirect()) && CollUtil.isNotEmpty(node.getChildren())) {
            NavigationRoute firstChild = node.getChildren().getFirst();
            if (firstChild != null && StrUtil.isNotBlank(firstChild.path)) {
                node.setRedirect(firstChild.getPath());
            }
        }

        if (CollUtil.isNotEmpty(node.getChildren())) {
            for (NavigationRoute child : node.getChildren()) {
                enrichNavigationNode(child);
            }
        }
    }

    private String resolveComponent(NavigationRoute node, NavigationRouteMeta meta) {
        if (CollUtil.isNotEmpty(node.getChildren())) {
            return "BasicLayout";
        }

        Integer type = node.getType();
        if (type == null) {
            return null;
        }

        String url = node.getUrl();

        if (type.equals(AuthorityConstants.MenuType.EXTERNAL_LINK.getCode())) {
            if (StrUtil.isNotBlank(url)) {
                meta.putIfAbsent("link", url);
                return "IFrameView";
            }
            return null;
        }

        if (type.equals(AuthorityConstants.MenuType.EMBEDDED_PAGE.getCode())) {
            if (StrUtil.isNotBlank(url)) {
                meta.putIfAbsent("iframeSrc", url);
                return "IFrameView";
            }
            return null;
        }

        return null;
    }

    @Override
    public List<UserProfile> getUsersByRoleId(String roleId) {
        return authenticationMapper.selectUserProfileByRoleId(roleId);
    }

    @Override
    public AuthenticatedUser getUserInfo() {
        LoginUser loginUser = SecurityUtils.getUser();
        UserDetails userDetails = authenticationMapper.selectUserDetailByUsername(loginUser.getName());
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

        UserInfoEntity userInfoEntity = userInfoMapper.selectById(loginUser.getName());
        if (userInfoEntity != null) {
            user.setAvatar(userInfoEntity.getAvatar());
            user.setDesc(userInfoEntity.getRemark());
        }

        user.setToken(StpUtil.getTokenValue());
        user.setHomePath("/dashboard/analysis");

        return user;
    }

    @Override
    public List<String> getAuthorities() {
        LoginUser operator = OperatorUtils.getOperator();
        return authenticationMapper.selectAuthoritiesByUsername(operator.getName());
    }

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
