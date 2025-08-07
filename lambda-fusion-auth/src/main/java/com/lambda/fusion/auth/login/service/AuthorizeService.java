package com.lambda.fusion.auth.login.service;



import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.auth.NavigationParameter;
import com.lambda.fusion.auth.resource.model.Resource;
import com.lambda.fusion.auth.user.domain.SimpleUser;
import com.lambda.security.service.ThirdPartyLoginService;
import com.lambda.security.service.UserDetailService;

import java.util.List;


public interface AuthorizeService extends UserDetailService, ThirdPartyLoginService {

    /***
     *
     * 获取用户的菜单，可以指定筛选条件
     *
     * @param user
     * @param parentId 父级菜单，只返回此菜单下的数据
     * @param level    制定菜单层级
     */
    List<Resource> getNavigation(LoginUser user, String parentId, Integer level);

    /***
     *
     * 获取用户的菜单，可以指定筛选条件
     *
     * @param user
     * @param parameter 资源参数
     */
    default List<Resource> getNavigation(LoginUser user, NavigationParameter parameter){
        return getNavigation(user, parameter.getParentId(), parameter.getLevel());
    }

    /***
     * 根据角色获取角色列表
     *
     * @param roleid
     */
    List<SimpleUser> getUsersByRoleId(String roleid);

}
