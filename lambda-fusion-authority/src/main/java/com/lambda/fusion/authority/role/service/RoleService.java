package com.lambda.fusion.authority.role.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.authority.role.model.AccessPermission;
import com.lambda.fusion.authority.role.model.BatchAddRoleUser;
import com.lambda.fusion.authority.role.model.CreateRole;
import com.lambda.fusion.authority.role.model.Group;
import com.lambda.fusion.authority.role.model.GroupRole;
import com.lambda.fusion.authority.role.model.Role;
import com.lambda.fusion.authority.role.model.UpdateRole;
import com.lambda.fusion.core.user.Operator;
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
     * @param operator
     * @return
     */
    List<Role> getAllRoles(Operator operator);

    /**
     * 获取角色分组
     *
     * @param operator 用户
     * @param tenantId the tenantId
     * @return ig all group roles
     */
    List<GroupRole> getAllGroupRoles(Operator operator, String tenantId);

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
     * @param operator
     * @param role
     * @return
     * @throws Exception
     */
    Role updateRole(Operator operator, UpdateRole role);

    /**
     * 增加角色
     * 默认新增角色是启用状态
     *
     * @param operator
     * @param role
     * @return
     * @throws Exception
     */
    Role saveRole(Operator operator, CreateRole role);

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
     * @param operator 当前用户
     * @param id 角色id
     * @param mode 角色模式
     * @return
     */
    List<AccessPermission> getAccessPermissions(Operator operator, String id, Integer mode);

    /**
     * 保存角色权限
     *  @param authority
     * @param resourceid
     * @param status
     * @param operator
     */
    void saveAuthorization(String authority, String resourceid, int status, Operator operator);

    /**
     * 删除角色权限
     *
     * @param id
     * @param resourceid
     * @param operator
     */
    void deleteAuthorization(String id, String resourceid, Operator operator);

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
     * @param operator 当前操作用户
     * @param req  请求
     */
    void batchAddRoleUser(Operator operator, BatchAddRoleUser req);

    /**
     * 分组列表查询
     *
     * @param operator 当前用户
     * @return 列表
     */
    List<Group> listGroups(Operator operator);
}
