package com.lambda.fusion.authority.authentication.service;

import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.resource.model.Resource;
import com.lambda.fusion.authority.user.model.vo.SimpleUserVO;
import com.lambda.security.service.ThirdPartyLoginService;
import com.lambda.security.service.UserDetailService;
import java.util.List;

/**
 * 认证服务接口
 * 负责用户认证、授权和导航菜单相关的业务逻辑
 */
public interface AuthenticationService extends UserDetailService, ThirdPartyLoginService {

    /**
     * 获取用户的导航菜单
     *
     * @param user 登录用户
     * @param parentId 父级菜单ID，只返回此菜单下的数据
     * @param level 指定菜单层级
     * @return 导航菜单列表
     */
    List<Resource> getNavigation(LoginUser user, String parentId, Integer level);

    /**
     * 获取用户的导航菜单
     *
     * @param user 登录用户
     * @param query 导航查询参数
     * @return 导航菜单列表
     */
    default List<Resource> getNavigation(LoginUser user, NavigationQuery query) {
        return getNavigation(user, query.getParentId(), query.getLevel());
    }

    /**
     * 根据角色ID获取用户列表
     *
     * @param roleId 角色ID
     * @return 用户列表
     */
    List<SimpleUserVO> getUsersByRoleId(String roleId);
}
