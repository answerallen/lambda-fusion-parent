package com.lambda.fusion.authority.organization.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.organization.model.dto.OrganizationQueryDTO;
import com.lambda.fusion.authority.organization.model.entity.OrganizationEntity;
import com.lambda.fusion.authority.organization.model.vo.MutableOrganizationVO;
import com.lambda.fusion.authority.organization.model.vo.OrganizationTreeVO;
import com.lambda.fusion.authority.organization.model.vo.OrganizationVO;
import com.lambda.fusion.authority.user.model.vo.MutableUserVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrganizationMapper extends BaseMapper<OrganizationEntity> {

    /**
     * 查询用户关联组织角色
     *
     * @param users 用户信息
     * @return 用户信息列表
     */
    List<MutableOrganizationVO> getAllOrganMutableUsers(List<MutableUserVO> users);

    /**
     * 查询全部组织角色
     *
     * @param parameters 查询参数
     * @return 组织列表
     */
    List<OrganizationVO> getAllMutableOrgan(@Param("parameters") OrganizationQueryDTO parameters);

    /**
     * 查询所有可用组织
     *
     * @param parameters 查询参数
     * @return java.util.List<SimpleOrg>
     */
    List<OrganizationTreeVO> getAllEnabledOrgan(@Param("parameters") OrganizationQueryDTO parameters);

    /**
     * 查询组织、角色信息
     *
     * @param ids 组织、角色ID
     * @return 组织角色信息
     */
    List<OrganizationVO> queryOrganizationList(List<OrganizationVO> ids);

    /***
     * 根据编号查询组织详情
     * @param id 组织id
     */
    @InterceptorIgnore(tenantLine = "true")
    OrganizationVO queryOrganizationById(String id);

    /***
     * 根据租户编号查询组织详情
     * @param id 租户id
     */
    @InterceptorIgnore(tenantLine = "true")
    List<OrganizationVO> queryOrganizationByTenantId(String id);

    /**
     * 是否存在上级组织
     *
     * @param id 组织id
     * @return 是否存在上级组织
     */
    @InterceptorIgnore(tenantLine = "true")
    boolean existParent(@Param("id") String id);

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
    List<OrganizationVO> getSubOrgIdsById(String id);

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
    OrganizationVO getInfo(String id);

    /**
     * 根据条件查询组织
     *
     * @param parameters 条件参数
     * @return 组织
     */
    List<OrganizationVO> getOrgIdsByCondition(@Param("parameters") OrganizationQueryDTO parameters);

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
    void batchUpdateOrgsAfterMoved(List<OrganizationVO> changed);

    /**
     * 获取节点的直接下级
     *
     * @param id
     */
    List<OrganizationVO> directChildrenGetter(String id);

    /**
     * 获取节点的所有下级，包含下级的下级
     *
     * @param parentKeys
     */
    List<OrganizationVO> allChildrenGetter(String parentKeys);
}
