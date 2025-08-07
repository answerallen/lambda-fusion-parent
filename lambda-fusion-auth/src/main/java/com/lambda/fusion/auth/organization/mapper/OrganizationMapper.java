package com.lambda.fusion.auth.organization.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.lambda.fusion.auth.organization.domain.*;
import com.lambda.fusion.auth.user.domain.MutableUser;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationMapper {

    /**
     * 查询用户关联组织角色
     *
     * @param users 用户信息
     * @return 用户信息列表
     */
    List<MutableOrganization> getAllOrganMutableUsers(List<MutableUser> users);

    /**
     * 查询全部组织角色
     *
     * @param parameters 查询参数
     * @return 组织列表
     */
    List<Organization> getAllMutableOrgan(@Param("parameters") Parameters parameters);

    /**
     * 查询所有可用组织
     *
     * @param parameters 查询参数
     * @return java.util.List<SimpleOrg>
     */
    List<SimpleOrg> getAllEnabledOrgan(@Param("parameters") Parameters parameters);

    /**
     * 查询组织、角色信息
     *
     * @param ids 组织、角色ID
     * @return 组织角色信息
     */
    List<Organization> queryMutableOrgan(List<Organization> ids);

    /***
     * 根据编号查询组织详情
     * @param id 组织id
     */
    @InterceptorIgnore(tenantLine = "true")
    Organization queryOrganizationById(String id);

    /***
     * 根据租户编号查询组织详情
     * @param id 租户id
     */
    @InterceptorIgnore(tenantLine = "true")
    List<Organization> queryOrganizationByTenantId(String id);

    /**
     * 条件查询
     *
     * @param organization 查询条件
     * @return 组织列表
     */
    List<Organization> queryByCondition(@Param("organization") Organization organization);

    /**
     * 添加组织
     *
     * @param organization 组织信息
     */
    void addOrganization(Organization organization);

    /**
     * 是否存在上级组织
     *
     * @param id 组织id
     * @return 是否存在上级组织
     */
    @InterceptorIgnore(tenantLine = "true")
    boolean existParent(@Param("id") String id);

    /**
     * 通过主键删除
     *
     * @param id 主键
     */
    void deleteOrganizationByPk(String id);

    /**
     * 删除多租户机构创建的内置角色
     *
     * @param id 主键
     */
    @InterceptorIgnore(tenantLine = "true")
    void deleteTenantOrganizationRole(String id);

    /**
     * 通过主键更新
     *
     * @param resource 主键查询
     */
    void updateOrganizationByPk(Organization resource);

    /**
     * 查询用户关联组织
     *
     * @param userId 用户ID
     * @return 用户组织信息
     */
    UserOrganization queryUserOrganization(String userId);

    /**
     * 用户关联组织
     *
     * @param userOrganization 用户组织信息
     */
    void addUserOrganization(UserOrganization userOrganization);

    /**
     * 更新用户组织
     *
     * @param userOrganization 用户组织信息
     */
    void updateUserOrganization(UserOrganization userOrganization);

    /**
     * 根据用户删除用户组织关系
     *
     * @param username 用户编号
     */
    @InterceptorIgnore(tenantLine = "true")
    void deleteUserOrganizationByUser(String username);

    /**
     * 根据组织删除用户组织关系
     *
     * @param orgId 组织编号
     */
    @InterceptorIgnore(tenantLine = "true")
    void deleteUserOrganizationByOrg(String orgId);

    /**
     * 获取指定节点的所有子节点
     *
     * @param id 组织id
     * @return 子组织id列表
     */
    @InterceptorIgnore(tenantLine = "true")
    List<String> getChildrenById(String id);

    /**
     * 获取指定节点的所有子节点
     *
     * @param id 组织id
     * @return 子组织列表
     */
    @InterceptorIgnore(tenantLine = "true")
    List<Organization> getSubOrgansById(String id);

    /**
     * 批量禁用/启用组织机构
     *
     * @param enabled 启用/禁用
     * @param ids     组织id列表
     **/
    @InterceptorIgnore(tenantLine = "true")
    void prohibitOrganizationByIds(@Param("enabled") Integer enabled, @Param("ids") List<String> ids);

    /**
     * "租户"类型机构 禁用/启用组织机构下用户
     *
     * @param enabled 禁用/启用
     * @param ids     组织id列表
     **/
    @InterceptorIgnore(tenantLine = "true")
    void prohibitOrgUsersByTenantOrgan(@Param("enabled") Integer enabled, @Param("ids") List<String> ids);

    /**
     * "普通"类型机构 禁用/启用组织机构下用户
     *
     * @param enabled 禁用/启用
     * @param ids     组织id列表
     */
    @InterceptorIgnore(tenantLine = "true")
    void prohibitOrgUsersByOrdinaryOrgan(@Param("enabled") Integer enabled, @Param("ids") List<String> ids);

    /**
     * 批量禁用/启用非组织机构下所有角色
     *
     * @param enabled 禁用/启用
     * @param ids     组织id列表
     **/
    @InterceptorIgnore(tenantLine = "true")
    void prohibitRoleByOrganizationByIds(@Param("enabled") Integer enabled, @Param("ids") List<String> ids);

    /**
     * 当前机构下是否存在用户
     *
     * @param ids 组织编码列表
     * @return {@link boolean}
     **/
    @InterceptorIgnore(tenantLine = "true")
    boolean existUser(@Param("ids") List<String> ids);

    /***
     * 当前机构下是否存在多租户组织
     * @param orgId 组织id
     * @return {@link boolean}
     **/
    @InterceptorIgnore(tenantLine = "true")
    boolean existTenantOrg(@Param("orgId") String orgId);

    /**
     * 根据ID获取信息
     *
     * @param id 组织id
     * @return 组织对象
     */
    Organization getInfo(String id);

    /**
     * 根据组织id批量获取根节点信息
     *
     * @param ids 组织id
     * @return List<Organization>
     */
    List<Organization> getOrgansByIds(@Param("ids") Set<String> ids);

    /**
     * 根据条件查询组织
     *
     * @param parameters 条件参数
     * @return 组织
     */
    List<Organization> getOrgansByCondition(@Param("parameters") Parameters parameters);

    /**
     * 根据父id更改spid
     *
     * @param id   父组织id
     * @param name 父组织编码
     */
    void updateChildrensSpid(String id, String name);

    /**
     * 根据组织ID删除组织
     *
     * @param ids 组织编号
     */
    @InterceptorIgnore(tenantLine = "true")
    void deleteOrgByIdList(@Param("ids") List<String> ids);

    /**
     * 根据组织ID删除用户组织关系
     *
     * @param ids 组织编号
     */
    @InterceptorIgnore(tenantLine = "true")
    void deleteUserOrgByIdList(@Param("ids") List<String> ids);

    /**
     * 批量更新移动后需要变更的节点
     *
     * @param changed
     */
    void batchUpdateOrgsAfterMoved(List<Organization> changed);

    /**
     * 获取节点的直接下级
     *
     * @param id
     */
    List<Organization> directChildrenGetter(String id);

    /**
     * 获取节点的所有下级，包含下级的下级
     *
     * @param parentkeys
     */
    List<Organization> allChildrenGetter(String parentkeys);
}
