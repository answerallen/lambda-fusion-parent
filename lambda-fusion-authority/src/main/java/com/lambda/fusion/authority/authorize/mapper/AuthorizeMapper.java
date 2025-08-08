package com.lambda.fusion.authority.authorize.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.lambda.fusion.authority.authorize.model.SimpleUser;
import com.lambda.fusion.authority.organization.domain.Organization;
import com.lambda.fusion.authority.resource.model.Resource;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthorizeMapper {

    /**
     * #根据用户名查询用户信息
     *
     * @param username
     * @return
     * @throws Exception
     */
    @InterceptorIgnore(tenantLine = "true")
    SimpleUser loadUserDetailByUsername(@Param("username") String username);

    /**
     * 根据手机号查询用户
     *
     * @param mobile
     * @return
     */
    List<SimpleUser> loadUserDetailByMobile(@Param("mobile") String mobile);

    /**
     * 根据用户id和条件获取用户菜单<br/>
     * level == null且parentId不为空时，将查询level小于id为parentId值的记录level的数据
     *
     * @param parameters
     */
    List<Resource> getNavigationByParams(Map<String, Object> parameters);

    /**
     * 获取所有资源，只包含id、名称、扩展参数。只查询ROLE_ADMIN的权限范围
     * @param parameters
     * @return
     */
    List<Resource> getAllResourcesSimple(Map<String, Object> parameters);

    /***
     * 根据用户名查询所属组织
     * @param username
     */
    Organization queryOrganizationByUser(String username);

    /***
     * 根据角色名称查询用户列表
     *
     * @param roleid
     */
    List<com.lambda.fusion.authority.user.domain.SimpleUser> getUsersByRoleId(@Param("roleid") String roleid);
}
