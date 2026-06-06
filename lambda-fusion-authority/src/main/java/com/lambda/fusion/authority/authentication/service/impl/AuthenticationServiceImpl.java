package com.lambda.fusion.authority.authentication.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.authority.AuthorityConstants;
import com.lambda.fusion.authority.authentication.mapper.AuthenticationMapper;
import com.lambda.fusion.authority.authentication.model.*;
import com.lambda.fusion.authority.authentication.service.AuthenticationService;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.model.UserAuthority;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.mapper.UserMapper;
import com.lambda.fusion.authority.user.mapper.UserThirdpartMapper;
import com.lambda.fusion.authority.user.model.User;
import com.lambda.fusion.authority.user.model.UserProfile;
import com.lambda.fusion.authority.user.model.entity.UserInfoEntity;
import com.lambda.fusion.authority.utils.AuthorityHelper;
import com.lambda.fusion.config.ConfigProperties;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.utils.AuthUtils;
import com.lambda.security.exception.AuthenticationException;
import com.lambda.security.exception.UsernameNotFoundException;
import com.lambda.security.provider.ThirdPartLoginResult;
import com.lambda.security.service.ThirdPartyLoginService;
import com.lambda.security.service.UserDetailService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.lambda.fusion.core.FusionConstants.AT;
import static com.lambda.fusion.core.FusionConstants.ROLE_TENANT;

/**
 * 认证服务实现类
 * 负责用户认证、授权和导航菜单相关的业务逻辑实现
 */
@Slf4j
@Primary
@Service
@SuppressFBWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService, UserDetailService, ThirdPartyLoginService {

    private final AuthenticationMapper authenticationMapper;
    private final UserInfoMapper userInfoMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final ConfigProperties configProperties;
    private final UserThirdpartMapper userThirdpartMapper;

    @Override
    public LoginUser loginByUsername(String username, String loginType) {
        if (username == null) {
            throw AuthorityBusinessException.invalidParameter("username不能为空");
        }
        AuthUser authUser = authenticationMapper.selectUserDetailByUsername(username);
        if (authUser == null) {
            throw AuthorityBusinessException.userNotFound(username);
        }
        return prepareLoginUser(authUser.toUserDetails());
    }

    @Override
    @Cacheable(value = "auth:role", key = "#loginId.toString()")
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roles = new ArrayList<>();
        List<UserAuthority> authorities = roleMapper.getUserAuthorityByUsername(loginId.toString());
        if (CollUtil.isEmpty(authorities)) {
            roles.add(FusionConstants.ROLE_USER);
        } else {
            authorities.forEach(userAuthority -> {
                if (userAuthority.getAuthority().startsWith(ROLE_TENANT)) {
                    String tenantAuthority = ROLE_TENANT + AT + userAuthority.getOrgId();
                    roles.add(tenantAuthority);
                } else {
                    roles.add(userAuthority.getAuthority());
                }
            });
        }
        return roles;
    }

    @Override
    @Cacheable(value = "auth:permission", key = "#loginId.toString()")
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> authorities = authenticationMapper.selectAuthoritiesByUsername(loginId.toString());
        List<String> permissions = authenticationMapper.selectPermissionsByUsername(loginId.toString());
        if (CollUtil.isNotEmpty(permissions)) {
            authorities.addAll(permissions);
        }

        User user = userMapper.selectUserByUsername(loginId.toString());
        if (AuthorityHelper.isTenantManager(user)) {
            List<String> tenantAdminPermissions =
                    authenticationMapper.selectTenantAdminPermissions(ROLE_TENANT + AT + user.getTenantId());
            if (CollUtil.isNotEmpty(tenantAdminPermissions)) {
                authorities.addAll(tenantAdminPermissions);
            }
            List<String> tenantAdminAuthorities =
                    authenticationMapper.selectTenantAdminAuthorities(ROLE_TENANT + AT + user.getTenantId());
            if (CollUtil.isNotEmpty(tenantAdminAuthorities)) {
                authorities.addAll(tenantAdminAuthorities);
            }
        }

        return authorities;
    }

    @Override
    public LoginUser loginByMobile(String mobile, String loginType) throws AuthenticationException {
        List<AuthUser> details = authenticationMapper.selectUserDetailsByMobile(mobile);
        AuthUser user = Optional.ofNullable(details)
                .filter(CollUtil::isNotEmpty)
                .filter(d -> d.size() == 1)
                .map(List::getFirst)
                .orElseThrow(() -> new UsernameNotFoundException("手机号不存在！"));
        return prepareLoginUser(user.toUserDetails());
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
                String path = StrUtil.removePrefix(node.getPath(), "/");
                if (StrUtil.endWith(path, "/")) {
                    path = StrUtil.removeSuffix(path, "/");
                    path = path + "/index";
                }
                node.setComponent(path);
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
        LoginUser loginUser = AuthUtils.getUser();
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
        user.setHomePath(configProperties.getApplication().getHomePath());

        return user;
    }

    @Override
    public List<String> getUserPermissions() {
        return authenticationMapper.selectAuthoritiesByUsername(
                AuthUtils.getUser().getName());
    }

    private LoginUser prepareLoginUser(UserDetails userDetails) {
        Set<String> roles = userDetails.getRoles();
        if (CollUtil.isEmpty(roles)) {
            userDetails.setRoles(Sets.newHashSet(FusionConstants.ROLE_USER));
        } else {
            userDetails.getRoles().stream()
                    .filter(role -> role.startsWith(ROLE_TENANT))
                    .findFirst()
                    .ifPresent(role -> {
                        String tenantAuthority = ROLE_TENANT + AT + userDetails.getOrgId();
                        userDetails.setRoles(Sets.newHashSet(tenantAuthority));
                    });
        }
        Optional.ofNullable(TenantHolder.getTenantId())
                .filter(StrUtil::isNotBlank)
                .ifPresent(userDetails::setTenantId);

        return userDetails;
    }

    @Override
    public LoginUser loadByThirdLoginResult(ThirdPartLoginResult thirdLoginResult, String loginType) {
        ThirdPartyInfo thirdPartyInfo = thirdLoginResult.getBody(ThirdPartyInfo.class);
        if (thirdPartyInfo == null) {
            return null;
        }
        String username =
                userThirdpartMapper.findUsernameByLoginTypeAndOpenId(thirdPartyInfo.getThirdType(), thirdPartyInfo.getOpenId());
        if (username == null) {
            //增加配置开关如果用户不存在，创建新用户
            return null;
        }
        return loginByUsername(username, loginType);
    }
}
