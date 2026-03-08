package com.lambda.fusion.authority.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.mapper.AuthenticationMapper;
import com.lambda.fusion.authority.mapper.UserInfoMapper;
import com.lambda.fusion.authority.model.authentication.*;
import com.lambda.fusion.authority.model.user.UserInfoEntity;
import com.lambda.fusion.authority.model.user.UserProfile;
import com.lambda.fusion.authority.service.AuthenticationService;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.utils.SecurityUtils;
import com.lambda.security.exception.AuthenticationException;
import com.lambda.security.exception.UsernameNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        if (username == null) {
            throw AuthorityBusinessException.invalidParameter("username不能为空");
        }
        AuthUser authUser = authenticationMapper.selectUserDetailByUsername(username);
        if (authUser == null) {
            throw AuthorityBusinessException.userNotFound(username);
        }
        UserDetails userDetails = authUser.toLoginUser();
        return prepareLoginUser(userDetails);
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
        List<AuthUser> details = authenticationMapper.selectUserDetailsByMobile(mobile);
        AuthUser user = Optional.ofNullable(details)
                .filter(CollUtil::isNotEmpty)
                .filter(d -> d.size() == 1)
                .map(List::getFirst)
                .orElseThrow(() -> new UsernameNotFoundException("手机号不存在！"));
        return prepareLoginUser(user.toLoginUser());
    }

    @Override
    public List<MenuRoute> getUserMenus(UserDetails loginUser, String parentId, Integer level) {
        MenuQuery query = new MenuQuery();
        query.setParentId(parentId);
        query.setLevel(level);
        query.setMode(0);
        if (!loginUser.isDev()) {
            query.setIds(new ArrayList<>(loginUser.getRoles()));
        }
        return getUserMenus(loginUser, query);
    }

    @Override
    public List<MenuRoute> getUserMenus(UserDetails userDetails, MenuQuery query) {
        List<MenuRoute> menuRoutes = authenticationMapper.selectNavigation(query);
        List<MenuRoute> menuRouteTree = TreeBuilder.build(menuRoutes);
        enrichNavigationRoutes(menuRouteTree);
        return menuRouteTree;
    }

    private void enrichNavigationRoutes(List<MenuRoute> menuRouteTree) {
        if (CollUtil.isEmpty(menuRouteTree)) {
            return;
        }
        for (MenuRoute root : menuRouteTree) {
            enrichNavigationNode(root);
        }
    }

    private void enrichNavigationNode(MenuRoute node) {
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
            MenuRoute firstChild = node.getChildren().getFirst();
            if (firstChild != null && StrUtil.isNotBlank(firstChild.path)) {
                node.setRedirect(firstChild.getPath());
            }
        }

        if (CollUtil.isNotEmpty(node.getChildren())) {
            for (MenuRoute child : node.getChildren()) {
                enrichNavigationNode(child);
            }
        }
    }

    private String resolveComponent(MenuRoute node, NavigationRouteMeta meta) {
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
        AuthUser authUser = authenticationMapper.selectUserDetailByUsername(loginUser.getName());
        if (authUser == null) {
            throw new UsernameNotFoundException("user not found");
        }

        AuthenticatedUser user = new AuthenticatedUser();
        BeanUtil.copyProperties(authUser, user);

        user.setUserId(authUser.getUsername());
        user.setRealName(authUser.getNickname());

        if (CollUtil.isNotEmpty(authUser.getAuthorities())) {
            user.setRoles(new ArrayList<>(authUser.getAuthorities()));
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
    public List<String> getUserPermissions() {
        return authenticationMapper.selectAuthoritiesByUsername(
                SecurityUtils.getUser().getName());
    }

    private LoginUser prepareLoginUser(UserDetails userDetails) {
        Set<String> roles = userDetails.getRoles();
        if (CollUtil.isEmpty(roles)) {
            userDetails.setRoles(Sets.newHashSet(FusionConstants.ROLE_USER));
        } else {
            userDetails.getRoles().stream()
                    .filter(role -> role.startsWith(FusionConstants.ROLE_TENANT))
                    .findFirst()
                    .ifPresent(role -> {
                        String tenantAuthority =
                                FusionConstants.ROLE_TENANT + FusionConstants.AT + userDetails.getOrgId();
                        userDetails.setRoles(Sets.newHashSet(tenantAuthority));
                    });
        }
        Optional.ofNullable(TenantHolder.getTenantId())
                .filter(StrUtil::isNotBlank)
                .ifPresent(userDetails::setTenantId);

        return userDetails;
    }
}
