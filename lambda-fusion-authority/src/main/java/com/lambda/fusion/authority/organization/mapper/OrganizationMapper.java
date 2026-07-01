package com.lambda.fusion.authority.organization.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.organization.model.*;
import com.lambda.fusion.authority.organization.model.entity.OrganizationEntity;
import com.lambda.fusion.authority.user.model.User;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface OrganizationMapper extends BaseMapper<OrganizationEntity> {

    /**
     * 查询用户关联组织角色
     *
     * @param users 用户信息
     * @return 用户信息列表
     */
    List<OrganizationWithUser> selectOrganizationByUsers(List<User> users);

    /**
     * 查询全部组织角色
     *
     * @param parameters 查询参数
     * @return 组织列表
     */
    List<Organization> selectOrganizations(@Param("parameters") OrganizationQuery parameters);

    /**
     * 查询所有可用组织
     *
     * @param parameters 查询参数
     * @return java.util.List<OrganizationTree>
     */
    List<OrganizationTree> selectEnabledOrganization(@Param("parameters") OrganizationQuery parameters);

    /**
     * 查询组织、角色信息
     *
     * @param ids 组织、角色ID
     * @return 组织角色信息
     */
    List<Organization> selectOrganizationList(List<Organization> ids);

    /***
     * 根据编号查询组织详情
     * @param id 组织id
     */
    Organization selectOrganizationById(String id);

    /***
     * 根据租户编号查询组织详情
     * @param id 租户id
     */
    List<Organization> selectOrganizationByTenantId(String id);

    /**
     * 是否存在上级组织
     *
     * @param id 组织id
     * @return 是否存在上级组织
     */
    boolean existParent(@Param("id") String id);

    /**
     * 获取指定节点的所有子节点
     *
     * @param id 组织id
     * @return 子组织id列表
     */
    List<String> selectChildrenByOrganizationId(String id);

    /**
     * 获取指定节点的所有子节点
     *
     * @param id 组织id
     * @return 子组织列表
     */
    List<Organization> selectSubOrganizationsById(String id);

    /**
     * 批量禁用/启用组织机构
     *
     * @param enabled 启用/禁用
     * @param ids     组织id列表
     **/
    void updateEnabledOrganizationByIds(@Param("enabled") Integer enabled, @Param("ids") List<String> ids);

    /**
     * "租户"类型机构 禁用/启用组织机构下用户
     *
     * @param enabled 禁用/启用
     * @param ids     组织id列表
     **/
    void updateEnabledOrganizationUsersByTenantIds(@Param("enabled") Integer enabled, @Param("ids") List<String> ids);

    /**
     * "普通"类型机构 禁用/启用组织机构下用户
     *
     * @param enabled 禁用/启用
     * @param ids     组织id列表
     */
    void updateEnabledOrganizationUsersByOrgIds(@Param("enabled") Integer enabled, @Param("ids") List<String> ids);

    /**
     * 批量禁用/启用非组织机构下所有角色
     *
     * @param enabled 禁用/启用
     * @param ids     组织id列表
     **/
    void updateEnabledRoleByOrganizationByIds(@Param("enabled") Integer enabled, @Param("ids") List<String> ids);

    /**
     * 当前机构下是否存在用户
     *
     * @param ids 组织编码列表
     * @return {@link boolean}
     **/
    boolean existUser(@Param("ids") List<String> ids);

    /***
     * 当前机构下是否存在多租户组织
     * @param orgId 组织id
     * @return {@link boolean}
     **/
    boolean existTenantOrganization(@Param("orgId") String orgId);

    /**
     * 根据条件查询组织
     *
     * @param organizationQuery 条件参数
     * @return 组织
     */
    List<Organization> selectOrganizationsByQuery(@Param("parameters") OrganizationQuery organizationQuery);

    /**
     * 根据组织ID删除组织
     *
     * @param ids 组织编号
     */
    void deleteOrgByIdList(@Param("ids") List<String> ids);

    /**
     * 根据组织ID删除用户组织关系
     *
     * @param ids 组织编号
     */
    void deleteUserOrgByIdList(@Param("ids") List<String> ids);

    /**
     * 批量更新移动后需要变更的节点
     *
     */
    void updateAffectedNodesAfterMove(List<Organization> changed);

    /**
     * 获取节点的直接下级
     *
     */
    List<Organization> selectChildren(String id);

    /**
     * 获取节点的所有下级，包含下级的下级
     *
     */
    List<Organization> selectOrganizationsByParentKeys(String parentKeys);
}
