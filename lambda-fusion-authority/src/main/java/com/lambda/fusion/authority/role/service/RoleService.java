package com.lambda.fusion.authority.role.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.authority.role.model.AccessPermission;
import com.lambda.fusion.authority.role.model.BatchRoleUserAssignmentRequest;
import com.lambda.fusion.authority.role.model.CreateRole;
import com.lambda.fusion.authority.role.model.Group;
import com.lambda.fusion.authority.role.model.GroupRole;
import com.lambda.fusion.authority.role.model.Role;
import com.lambda.fusion.authority.role.model.UpdateRole;
import com.lambda.fusion.core.identity.UserPrincipal;
import java.util.List;
import java.util.Map;

/**
 * 用户角色服务层接口
 *
 */
public interface RoleService {
    /**
     * 获取用户的所有角色信息
     *
     * @param userPrincipal
     * @return
     */
    List<Role> getAllRoles(UserPrincipal userPrincipal);

    /**
     * 获取角色分组
     *
     * @param userPrincipal 用户
     * @param tenantId the tenantId
     * @return ig all group roles
     */
    List<GroupRole> grouped(UserPrincipal userPrincipal, String tenantId);

    /***
     * 根据条件分页查询角色列表
     *
     * @param pageable
     * @param parameters
     * @return Page<MutableRole>
     */
    Page<Role> getAllRoles(Page<Role> pageable, Map<String, Object> parameters);

    /**
     * 修改角色
     *
     * @param userPrincipal
     * @param role
     * @return
     * @throws Exception
     */
    Role updateRole(UserPrincipal userPrincipal, UpdateRole role);

    /**
     * 增加角色
     * 默认新增角色是启用状态
     *
     * @param userPrincipal
     * @param role
     * @return
     * @throws Exception
     */
    Role saveRole(UserPrincipal userPrincipal, CreateRole role);

    /**
     * 根据角色编号查询角色信息
     *
     * @param id
     * @return
     */
    Role getRoleByAuthority(String id);

    /**
     * 根据id删除指定的角色
     *
     * @param roleId
     * @return
     */
    void deleteRoleById(String roleId);

    /**
     * 查询角色是否已经存在
     *
     * @param id
     * @return
     */
    boolean hasExists(String id);

    /**
     * 查询角色权限
     * @param userPrincipal 当前用户
     * @param id 角色id
     * @param mode 角色模式
     * @return
     */
    List<AccessPermission> getPermissions(UserPrincipal userPrincipal, String id, Integer mode);

    /**
     * 保存角色权限
     *  @param authority
     * @param resourceId
     * @param status
     * @param userPrincipal
     */
    void grantRolePermission(String authority, String resourceId, int status, UserPrincipal userPrincipal);

    /**
     * 删除角色权限
     *
     * @param id
     * @param resourceId
     * @param userPrincipal
     */
    void revokeRolePermission(String id, String resourceId, UserPrincipal userPrincipal);

    /**
     * 查询该角色名是否被使用
     *
     * @param authority
     */
    boolean hasUsedAuthority(String authority);

    /**
     * 启用或禁用角色
     *
     * @param type
     * @param authority
     **/
    void prohibitRole(int type, String authority);

    /**
     * 获取机构为多租户的所有角色
     */
    List<Role> getTenantRolesByOwner(String owner);

    /**
     * 添加组
     *
     * @param group 分组信息
     * @return 结果
     */
    Group addGroup(Group group);

    /**
     * 删除分组信息
     *
     * @param groupId 分组ID
     */
    void deleteGroup(String groupId);

    /**
     * 更新分组信息
     *
     * @param group 分组信息
     * @return ig
     */
    Group updateGroup(Group group);

    /**
     * 根据组ID查询组信息
     *
     * @param id
     */
    Group getGroupById(String id);

    /**
     * 批量添加角色用户
     *
     * @param userPrincipal 当前操作用户
     * @param req  请求
     */
    void assignUsersToRole(UserPrincipal userPrincipal, BatchRoleUserAssignmentRequest req);

    /**
     * 分组列表查询
     *
     * @param userPrincipal 当前用户
     * @return 列表
     */
    List<Group> listGroups(UserPrincipal userPrincipal);
}
