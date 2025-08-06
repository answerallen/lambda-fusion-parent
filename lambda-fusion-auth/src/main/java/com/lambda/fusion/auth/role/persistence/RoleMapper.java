package com.lambda.fusion.auth.role.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.auth.role.bean.AccessPermission;
import com.lambda.fusion.auth.role.bean.AccessPermissionDO;
import com.lambda.fusion.auth.role.bean.UserAuthority;
import com.lambda.fusion.auth.role.bean.MutableRole;
import com.lambda.fusion.auth.role.bean.RoleAuthority;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface RoleMapper {
    /**
     * 获取所有角色
     *
     * @param parameters
     * @return
     */
    List<MutableRole> getAllRoles(Map<String, Object> parameters);

    /**
     * 根据条件分页查询用户角色记录
     *
     * @param page
     * @param parameters
     * @return
     */
    @InterceptorIgnore(tenantLine = "true")
    Page<MutableRole> getAllMutableRoles(Page<MutableRole> page, @Param("parameters") Map<String, Object> parameters);

    /**
     * 根据id查询角色信息
     *
     * @param authority
     * @return
     */
    MutableRole getRoleByAuthority(String authority);

    /**
     * 保存新角色
     *
     * @param role
     */
    void insertRole(MutableRole role);

    /**
     * 更新角色信息
     *
     * @param role
     */
    void updateRole(MutableRole role);

    /**
     * 根据authority删除指定的角色信息
     *
     * @param authority
     */
    void deleteRoleByAuthority(String authority);

    /**
     * 根据id删除角色相关的用户关系
     *
     * @param authority
     */
    void deleteUserRoleByAuthority(String authority);

    /**
     * 根据id删除角色相关的资源关系
     *
     * @param authority
     */
    void deleteResourceRoleByAuthority(String authority);

    /**
     * 根据authority查询是否可用
     *
     * @param authority
     * @return
     */
    boolean hasExists(String authority);

    /**
     * 获取已经拥有的资源编号
     *
     * @param authority
     * @return
     */
    List<String> hasAuthorized(@Param("authority") String authority);

    /**
     * 单个保存资源授权
     *
     * @param accessPermissionDO
     * @return void
     */
    void saveAuthorization(AccessPermissionDO accessPermissionDO);

    /**
     * 批量保存访问授权
     *
     * @param parameters
     * @return void
     */
    void batchSaveAuthorization(AccessPermissionDO parameters);

    /**
     * 批量更新访问权限
     *
     * @param parameters
     * @return void
     */
    void batchUpdateAuthorization(AccessPermissionDO parameters);

    /**
     * 删除资源授权
     *
     * @param authority
     * @param id
     */
    void deleteAuthorization(@Param("authority") String authority, @Param("id") String id);

    /***
     * 批量删除资源授权
     * @param authority
     * @param ids
     * @param tenantid
     * @return void
     */
    void batchDeleteAuthorization(@Param("authority") String authority, @Param("ids") List<String> ids, @Param("tenantid") String tenantid);

    /***
     * 根据当前节点查询所有已经拥有的直接子节点编号
     * @param authority
     * @param pid
     * @return java.util.List<java.lang.String>
     */
    List<String> hasChildrenChecked(@Param("authority") String authority, @Param("pid") String pid);

    /**
     * *查询该角色名是否被使用
     *
     * @param authority
     * @return
     */
    boolean hasUsedAuthority(@Param("authority") String authority);


    /**
     * 查询组织权限
     *
     * @param authority
     * @param pid
     * @return
     */
    List<RoleAuthority> getOrganizationAuthorization(@Param("organizationid") String authority, @Param("pid") String pid);

    /**
     * 判断是否已经授权
     *
     * @param authority
     * @param resourceid
     * @return
     */
    boolean hasOrganizationAuthorized(@Param("organizationid") String authority, @Param("resourceid") String resourceid);

    /**
     * 保存组织资源授权
     *
     * @param authority
     * @param resourceid
     */
    void saveOrganizationAuthorization(@Param("organizationid") String authority, @Param("resourceid") String resourceid);

    /**
     * 删除组织资源授权
     *
     * @param authority
     * @param resourceid
     */
    void deleteOrganizationAuthorization(@Param("organizationid") String authority, @Param("resourceid") String resourceid);


    /**
     * 启用禁用角色
     *
     * @param type
     * @param authority
     **/
    void prohibitRole(@Param("enabled") Integer type, @Param("authority") String authority);

    /**
     * 根据租户拥有者查询当前租户下所有角色
     *
     * @param owner
     * @return
     */
    List<MutableRole> getTenantRolesByOwner(@Param("owner") String owner);

    /**
     * 查询用户权限
     *
     * @param parameters
    */
    List<AccessPermission> getAccessPermissions(@Param("parameters") Map<String, Object> parameters);

    /**
     * 查询已有的访问权限
     *
     * @param authority
     * @param ids
     * @return java.util.List<java.lang.String>
     */
    List<String> hasAuthorizedWithIntersection(String authority, Set<String> ids);

    /**
     * 通过租户ID删除角色权限信息
     *
     * @param tenantIds 租户编号
     */
    void deleteRoleAuthorizeByTenantId(@Param("tenantIds") List<String> tenantIds);

    /**
     * 通过租户ID删除角色信息
     *
     * @param tenantIds 租户编号
     */
    void deleteRoleByTenantId(@Param("tenantIds") List<String> tenantIds);

    /**
     * 根据用户名查询该用户拥有哪些角色
     *
     * @param username
     * @return java.util.Set<java.lang.String>
     */
    @InterceptorIgnore(tenantLine = "true")
    List<UserAuthority> getAuthoritiesByUser(String username);
}
