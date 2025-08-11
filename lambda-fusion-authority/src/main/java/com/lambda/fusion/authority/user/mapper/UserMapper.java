package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.authority.user.model.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据持久层操作接口
 *
 */
@Mapper
public interface UserMapper {

    /**
     * 根据角色查询用户集合
     *
     * @param orgId
     * @param authority
     * @return
     */
    List<String> getUserNamesByAuthority(@Param("orgId") String orgId, @Param("authority") String authority);

    /**
     * 根据组织机构ID查询用户集合
     *
     * @param orgId 机构id
     * @param type  机构类型
     * @return 用户集合
     */
    List<String> getUsernameByOrgId(@Param("orgId") String orgId, @Param("type") Integer type);

    /**
     * 根据用户名称查询用户信息
     *
     * @param username
     * @return
     * @throws Exception
     */
    MutableUser getMutableUserById(@Param("username") String username);

    /**
     * 根据用户名称获取密码
     *
     * @param username
     * @return
     * @throws Exception
     */
    MutableUser getPasswordById(@Param("username") String username);

    /**
     * 查询所有用户
     *
     * @param users
     * @return
     */
    List<MutableUser> getAllMutableUsers(List<MutableUser> users);

    /**
     * 查询所有用户
     *
     * @param page
     * @param parameters
     * @return
     */
    Page<MutableUser> getAllMutableUsersByCondition(Page<MutableUser> page, Map<String, Object> parameters);

    /**
     * 根据关键字模糊查询用户列表
     *
     * @param key
     * @return
     */
    List<MutableUser> getAllMutableUsersByKey(@Param("key") String key);

    /**
     * 保存用户
     *
     * @param user
     */
    void insertMutableUser(MutableUser user);

    /**
     * 添加用户角色
     *
     * @param username
     * @param authority
     * @param tenantId
     *
     * @date 2019-03-22
     */
    void addUserRole(
            @Param("username") String username,
            @Param("authority") String authority,
            @Param("tenant_id") String tenantId);

    /**
     * 更新用户
     *
     * @param user
     */
    void updateMutableUser(MutableUser user);

    /**
     * 修改指定用户的密码
     *
     * @param user
     */
    void updatePassword(MutableUser user);

    /**
     * 删除用户
     *
     * @param username
     */
    void deleteUser(@Param("username") String username);

    /**
     * 删除用户关联角色
     *
     * @param username
     */
    void deleteUserRoles(@Param("username") String username);

    /**
     * 检查用户名是否已经存在
     *
     * @param username
     * @return
     */
    boolean hasExists(String username);

    /**
     * 启用禁用用户
     *
     * @param type     : 1 :启用 ; 0 :禁用
     * @param type
     * @param username
     */
    void prohibitUser(@Param("enabled") Integer type, @Param("username") String username);

    /**
     * 查询所有用户信息
     **/
    List<MutableUser> getAllUsers();

    /**
     * 获取组织机构下所有用户
     *
     * @param orgId
     **/
    List<MutableUser> getAllOrgUsers(@Param("orgId") String orgId);

    /**
     * 根据组织查询用户列表
     *
     * @param orgid
     * @param roleid
     * @return
     */
    List<String> getUidsByOrg(String orgid, String roleid);

    /**
     * 根据用户ID和角色查询所有权限
     *
     * @param ids
     * @param mode
     */
    List<Permission> getAllUserPermissions(List<String> ids, @Param("mode") String mode);

    /**
     * 查询用户的权限
     *
     * @param source
     * @return java.util.List<java.lang.String>
     */
    Set<String> getUserPermissions(String source);

    /**
     * 获取权限数据
     *
     * @param authority   角色或用户
     * @param manage      是否管理权限
     * @param permissions 权限
     */
    List<RoleResources> getRoleResources(
            @Param("authority") String authority,
            @Param("manage") String manage,
            @Param("permissions") Set<String> permissions);

    /**
     * 批量保存用户权限
     *
     * @param target        用户或角色
     * @param roleResources 权限
     * @param tenantId      租户编号
     *
     */
    void saveUserPermission(
            @Param("uid") String target,
            @Param("roleResources") RoleResources roleResources,
            @Param("tenant_id") String tenantId);

    /**
     * 批量更新用户权限
     *
     * @param target      用户或角色
     * @param manage      是否管理权限
     * @param permissions 权限
     * @param tenantId    租户编号
     *
     */
    void batchUpdateUserPermissions(
            @Param("uid") String target,
            @Param("manage") String manage,
            @Param("permissions") List<RoleResources> permissions,
            @Param("tenant_id") String tenantId);

    /**
     * 获取用户下拉列表数据
     *
     * @param tenantId
     * @param organs
     * @return
     */
    List<SimpleUser> getAllSimpleUser(@Param("tenantId") String tenantId, List<String> organs);

    /**
     * 查询所有用户
     *
     * @param parameters
     * @return
     */
    List<MutableUser> getAllMutableUsersNoPage(@Param("parameters") Map<String, Object> parameters);

    /**
     * 修改用户手机号
     *
     * @param user 用户修改信息
     */
    void updateMobile(MutableUser user);

    /**
     * 修改用户邮箱
     *
     * @param user 用户修改信息
     */
    void updateEmail(MutableUser user);

    /**
     * 修改用户昵称
     *
     * @param user 用户修改信息
     */
    void updateInfo(RestUserInfoParameter user);

    /**
     * 根据租户ID，查询租户管理员列表
     *
     * @param tenantId 租户ID
     */
    @InterceptorIgnore(tenantLine = "true")
    List<MutableUser> getAllMutableUsersByTenantId(@Param("tenantId") String tenantId);

    /**
     * 检查手机号是否绑定用户
     *
     * @param username
     * @param mobile
     * @return boolean
     */
    boolean checkMobileExists(String username, String mobile);

    /**
     * 获取租户管理员的租户ID
     * @param username
     * @return
     */
    String getTenantIdByTenantAdmin(@Param("username") String username);
}
