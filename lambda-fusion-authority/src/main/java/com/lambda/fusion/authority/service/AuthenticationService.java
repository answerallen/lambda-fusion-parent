package com.lambda.fusion.authority.service;

import com.lambda.fusion.authority.domain.authentication.AuthenticatedUser;
import com.lambda.fusion.authority.domain.authentication.NavigationQuery;
import com.lambda.fusion.authority.domain.authentication.NavigationRoute;
import com.lambda.fusion.authority.domain.user.UserProfile;
import com.lambda.fusion.core.identity.LoginUserDetails;
import com.lambda.fusion.core.utils.SecurityUtils;
import com.lambda.security.service.UserDetailService;
import java.util.List;

/**
 * 认证服务接口
 * 负责用户认证、授权和导航菜单相关的业务逻辑
 */
public interface AuthenticationService extends UserDetailService {

    /**
     * 获取用户的导航菜单
     *
     * @param user     登录用户
     * @param parentId 父级菜单ID，只返回此菜单下的数据
     * @param level    指定菜单层级
     * @return 导航菜单列表
     */
    List<NavigationRoute> getNavigation(LoginUserDetails user, String parentId, Integer level);

    /**
     * 获取用户的导航菜单
     *
     * @param user  登录用户
     * @param query 导航查询参数
     * @return 导航菜单列表
     */
    default List<NavigationRoute> getNavigation(LoginUserDetails user, NavigationQuery query) {
        return getNavigation(user, query.getParentId(), query.getLevel());
    }

    /**
     * 获取用户的导航菜单
     * @param query 导航查询参数
     * @return 导航菜单列表
     */
    default List<NavigationRoute> getNavigation(NavigationQuery query) {
        return getNavigation(SecurityUtils.getUser(), query.getParentId(), query.getLevel());
    }

    /**
     * 根据角色ID获取用户列表
     *
     * @param roleId 角色ID
     * @return 用户列表
     */
    List<UserProfile> getUsersByRoleId(String roleId);

    /**
     * 获取当前登录用户的信息。
     *
     * @return 返回包含用户详细信息的AuthenticatedUser对象，包括用户ID、真实姓名、角色列表、头像、描述、token以及主页路径等信息。
     */
    AuthenticatedUser getUserInfo();

    /**
     * 获取当前用户的权限码集合
     *
     * @return 权限码列表
     */
    List<String> getAuthorities();
}
