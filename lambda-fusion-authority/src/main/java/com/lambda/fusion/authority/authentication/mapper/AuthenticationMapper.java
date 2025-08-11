package com.lambda.fusion.authority.authentication.mapper;

import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.authentication.model.ResourceSimpleQuery;
import com.lambda.fusion.authority.authentication.model.domain.SimpleUser;
import com.lambda.fusion.authority.resource.model.Resource;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 认证数据访问层接口
 * 负责用户认证相关的数据库操作
 */
@Mapper
public interface AuthenticationMapper {

    /**
     * 根据用户名加载用户详细信息
     *
     * @param username 用户名
     * @return 用户详细信息
     */
    SimpleUser loadUserDetailByUsername(@Param("username") String username);

    /**
     * 根据手机号加载用户详细信息
     *
     * @param mobile 手机号
     * @return 用户详细信息列表
     */
    List<SimpleUser> loadUserDetailByMobile(@Param("mobile") String mobile);

    /**
     * 根据查询条件获取导航菜单
     *
     * @param query 导航查询参数
     * @return 导航菜单列表
     */
    List<Resource> getNavigationByQuery(NavigationQuery query);

    /**
     * 根据查询条件获取简单资源列表
     *
     * @param query 资源查询参数
     * @return 简单资源列表
     */
    List<Resource> getAllResourcesSimpleByQuery(ResourceSimpleQuery query);

    /**
     * 根据角色ID获取用户列表
     *
     * @param roleId 角色ID
     * @return 用户列表
     */
    List<com.lambda.fusion.authority.user.model.SimpleUser> getUsersByRoleId(@Param("roleId") String roleId);
}
