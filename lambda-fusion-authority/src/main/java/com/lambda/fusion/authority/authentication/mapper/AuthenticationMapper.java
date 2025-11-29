package com.lambda.fusion.authority.authentication.mapper;

import com.lambda.fusion.authority.authentication.model.AuthUserDetails;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.authentication.model.ResourceQuery;
import com.lambda.fusion.authority.resource.model.ResourceTree;
import com.lambda.fusion.authority.user.model.UserProfile;
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
    AuthUserDetails selectUserDetailByUsername(@Param("username") String username);

    /**
     * 根据手机号加载用户详细信息
     *
     * @param mobile 手机号
     * @return 用户详细信息列表
     */
    List<AuthUserDetails> selectUserDetailsByMobile(@Param("mobile") String mobile);

    /**
     * 根据查询条件获取导航菜单
     *
     * @param query 导航查询参数
     * @return 导航菜单列表
     */
    List<ResourceTree> selectNavigation(NavigationQuery query);

    /**
     * 根据查询条件获取简单资源列表
     *
     * @param query 资源查询参数
     * @return 简单资源列表
     */
    List<ResourceTree> selectResources(ResourceQuery query);

    /**
     * 根据角色ID获取用户列表
     *
     * @param roleId 角色ID
     * @return 用户列表
     */
    List<UserProfile> selectUserProfileByRoleId(@Param("roleId") String roleId);
}
