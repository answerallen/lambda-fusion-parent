package com.lambda.fusion.authority.service;

import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.model.organization.CreateOrganization;
import com.lambda.fusion.authority.model.organization.Organization;
import com.lambda.fusion.authority.model.organization.OrganizationQuery;
import com.lambda.fusion.authority.model.organization.OrganizationTree;
import com.lambda.fusion.authority.model.organization.OrganizationWithUser;
import com.lambda.fusion.authority.model.organization.UpdateOrganization;
import com.lambda.fusion.authority.model.organization.UserOrganization;
import com.lambda.fusion.authority.model.organization.UserOrganizationChange;
import com.lambda.fusion.authority.model.resource.MoveResource;
import com.lambda.fusion.authority.model.user.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public interface OrganizationService {

    /**
     * 以树形的方式获取组织权限列表
     *
     * @param parameters 查询参数
     * @return
     */
    List<Organization> organizationTreeList(OrganizationQuery parameters);

    /**
     * 获取当前用户所有子部门
     *
     * @param parameters
     * @return
     */
    List<Organization> getSubOrganizations(OrganizationQuery parameters);

    /**
     * 根据ID查询组织信息
     *
     * @param id
     */
    Organization queryOrganizationById(String id);

    /**
     * 新增组织信息
     *
     * @param resource 组织机构
     * @return {@link Organization}
     */
    Organization addOrganization(CreateOrganization resource);

    /***
     * 删除组织信息
     * @param id
     */
    void deleteOrganization(String id);

    /**
     * 更新组织信息
     *
     * @param resource 组织
     * @return {@link Organization}
     */
    Organization updateOrganization(UpdateOrganization resource);

    /**
     * 获取用户组织、角色信息
     *
     * @param users 用户信息
     * @return {@link Organization}
     */
    List<OrganizationWithUser> getAllOrganMutableUsers(List<User> users);

    /**
     * 以平铺的方式获取组织权限列表
     *
     * @param parameters 查询参数
     * @return ig
     */
    List<Organization> selectAll(OrganizationQuery parameters);

    /**
     * 查询用户组织信息
     *
     * @param resource 用户组织
     * @return {@link UserOrganizationChange}
     */
    UserOrganization queryUserOrganization(UserOrganizationChange resource);

    /**
     * 添加用户组织信息
     *
     * @param resource 用户组织
     * @return {@link UserOrganizationChange}
     */
    UserOrganization addUserOrganization(UserOrganizationChange resource);

    /**
     * 删除用户组织信息
     *
     * @param username 用户编号
     */
    void deleteUserOrganization(String username);

    /**
     * 更新用户组织关系
     *
     * @param resource 用户组织
     * @return {@link UserOrganizationChange}
     */
    UserOrganization updateUserOrganization(UserOrganizationChange resource);

    /**
     * 查询指定节点的所有子节点，包含自已及下级子节点
     *
     * @param id
     * @return
     */
    List<String> getChildrenById(String id);

    /**
     * 查询指定节点的所有父节点
     *
     * @param id
     * @return
     */
    List<String> getParentsById(String id);

    /**
     * 禁用/启用组织机构
     *
     * @param enabled
     * @param id
     */
    void prohibitOrganization(Integer enabled, String id);

    /**
     * 获取组织机构树
     *
     * @param parameters
     */
    List<OrganizationTree> getOrganizationTree(OrganizationQuery parameters);

    /**
     * 根据用户所在组织机构获取当前机构含子集id
     *
     * @return
     */
    List<String> getSubOrganizations(LoginUser operator);

    /**
     * 根据指定组织编号查询下级组织列表
     *
     * @param orgId
     * @return
     */
    List<String> getSubOrganizations(String orgId);

    /**
     * 通过excel来增加组织
     *
     * @param file     excel文件
     * @return 导入失败的数据
     */
    void addOrganizationByImport(MultipartFile file);

    /**
     * 根据组织id获取根节点信息
     *
     * @param id 组织id
     * @return Organization
     */
    Organization getRootOrganizationById(String id);

    /**
     * 根据提供的组织列表查询该组织都属于哪些公司
     *
     * @param ids 组织id
     * @return Map<String, Organization
     */
    Map<String, Organization> getOrganizationMapByIds(Set<String> ids);

    /**
     * 移动组织树节点
     *
     * @param parameter
     * @return void
     */
    void move(MoveResource parameter);
}
