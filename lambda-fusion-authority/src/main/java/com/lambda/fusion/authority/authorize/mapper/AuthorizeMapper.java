package com.lambda.fusion.authority.authorize.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.lambda.fusion.authority.authorize.model.dto.NavigationQueryDTO;
import com.lambda.fusion.authority.authorize.model.dto.ResourceSimpleQueryDTO;
import com.lambda.fusion.authority.authorize.model.vo.SimpleUserVO;
import com.lambda.fusion.authority.organization.model.Organization;
import com.lambda.fusion.authority.resource.model.Resource;
import com.lambda.fusion.authority.user.domain.SimpleUser;
import java.util.List;
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
    SimpleUserVO loadUserDetailByUsername(@Param("username") String username);

    /**
     * 根据手机号查询用户
     *
     * @param mobile
     * @return
     */
    List<SimpleUserVO> loadUserDetailByMobile(@Param("mobile") String mobile);

    /**
     * 根据用户id和条件获取用户菜单<br/>
     * level == null且parentId不为空时，将查询level小于id为parentId值的记录level的数据
     *
     * @param query 导航查询条件
     */
    List<Resource> getNavigationByQuery(@Param("query") NavigationQueryDTO query);

    /**
     * 获取简单资源列表（使用DTO对象）
     * @param query 查询参数
     * @return 资源列表
     */
    List<Resource> getAllResourcesSimpleByQuery(ResourceSimpleQueryDTO query);

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
    List<SimpleUser> getUsersByRoleId(@Param("roleid") String roleid);
}
