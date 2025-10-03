package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.authority.user.model.entity.UserEntity;
import com.lambda.fusion.authority.user.model.vo.MutableUserVO;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lambda.fusion.authority.user.model.vo.PermissionVO;
import com.lambda.fusion.authority.user.model.vo.RoleResourcesVO;
import com.lambda.fusion.authority.user.model.vo.SimpleUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据持久层操作接口
 *
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

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
    MutableUserVO getMutableUserById(@Param("username") String username);

    /**
     * 查询所有用户
     *
     * @param users
     * @return
     */
    List<MutableUserVO> getAllMutableUsers(List<MutableUserVO> users);

    /**
     * 查询所有用户
     *
     * @param page
     * @param parameters
     * @return
     */
    Page<MutableUserVO> getAllMutableUsersByCondition(Page<MutableUserVO> page, Map<String, Object> parameters);

    /**
     * 根据关键字模糊查询用户列表
     *
     * @param key
     * @return
     */
    List<MutableUserVO> getAllMutableUsersByKey(@Param("key") String key);

    /**
     * 保存用户
     *
     * @param user
     */
    @Deprecated
    void insertMutableUser(MutableUserVO user);

    /**
     * 添加用户角色
     *
     * @param username
     * @param authority
     * @param tenantId
     *
     * @date 2019-03-22
     */
    @Deprecated
    void addUserRole(
            @Param("username") String username,
            @Param("authority") String authority,
            @Param("tenant_id") String tenantId);

    /**
     * 更新用户
     *
     * @param user
     */
    void updateMutableUser(MutableUserVO user);

    /**
     * 修改指定用户的密码
     *
     * @param user
     */
    default void updatePassword(String username, String newPassword) {
        update(new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getUserid, username)
                .set(UserEntity::getPassword, newPassword));
    }

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
    @Deprecated
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
    List<MutableUserVO> getAllUsers();

    /**
     * 获取组织机构下所有用户
     *
     * @param orgId
     **/
    List<MutableUserVO> getAllOrgUsers(@Param("orgId") String orgId);

    /**
     * 根据组织查询用户列表
     *
     * @param orgId
     * @param roleId
     * @return
     */
    List<String> getUidsByOrg(@Param("orgId") String orgId, @Param("roleId") String roleId);

    /**
     * 根据用户ID和角色查询所有权限
     *
     * @param ids
     * @param mode
     */
    List<PermissionVO> getAllUserPermissions(List<String> ids, @Param("mode") String mode);

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
    List<RoleResourcesVO> getRoleResources(
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
            @Param("roleResources") RoleResourcesVO roleResources,
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
            @Param("permissions") List<RoleResourcesVO> permissions,
            @Param("tenant_id") String tenantId);

    /**
     * 获取用户下拉列表数据
     *
     * @param tenantId
     * @param orgIds
     * @return
     */
    List<SimpleUserVO> getAllSimpleUser(@Param("tenantId") String tenantId, List<String> orgIds);

    /**
     * 查询所有用户
     *
     * @param parameters
     * @return
     */
    List<MutableUserVO> getAllMutableUsersNoPage(@Param("parameters") Map<String, Object> parameters);

    /**
     * 修改用户手机号
     *
     * @param username 用户修改信息
     * @param mobile 用户修改信息
     */
    default void updateMobile(String username,String mobile){
        update(new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getUserid, username)
                .set(UserEntity::getMobile, mobile));
    }

    /**
     * 修改用户邮箱
     *
     * @param username 用户修改信息
     * @param email 用户修改信息
     */
    default void updateEmail(String username,String email){
        update(new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getUserid, username)
                .set(UserEntity::getEmail, email));
    }

    /**
     * 修改用户昵称
     *
     * @param username 用户修改信息
     * @param email 用户修改信息
     * @param nickname 用户修改信息
     */
    default void updateInfo(String username,String email,String nickname){
        update(new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getUserid, username)
                .set(UserEntity::getEmail, email)
                .set(UserEntity::getNickname, nickname))
        ;
    }

    /**
     * 根据租户ID，查询租户管理员列表
     *
     * @param tenantId 租户ID
     */
    @InterceptorIgnore(tenantLine = "true")
    List<MutableUserVO> getAllMutableUsersByTenantId(@Param("tenantId") String tenantId);

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
