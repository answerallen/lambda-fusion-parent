package com.lambda.fusion.authority.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.user.model.*;
import com.lambda.fusion.authority.user.model.dto.ResetPwdDTO;
import com.lambda.fusion.authority.user.model.entity.UserInfoEntity;
import com.lambda.fusion.core.user.User;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户服务层接口
 */
public interface UserService {

    /**
     * 根据角色查询用户名集合
     *
     * @param orgid     组织编号
     * @param authority 角色
     * @return 用户集合
     */
    List<String> getUserNamesByAuthority(String orgid, @NotBlank String authority);

    /**
     * 根据组织机构id查询用户名集合
     *
     * @param orgid 组织机构id
     * @param type  机构类型
     * @return
     */
    List<String> getUserNamesByOrgId(@NotBlank String orgid, Integer type);

    /**
     * 查询所有用户信息
     *
     * @param
     **/
    List<MutableUser> getAllUsers();

    /**
     * 注册用户
     *
     * @param user
     **/
    void registeredMutableUser(MutableUser user);

    /***
     * 根据username查询用户详情
     * @param username 用户名
     */
    MutableUser getMutableUserByUsername(String username);

    /***
     * 获取当前用户得详情
     */
    MutableUser getCurrentMutableUser(User operator);

    /***
     * 查询注册用户列表
     * @param users 用户列表
     */
    List<MutableUser> getAllMutableUsers(List<MutableUser> users);

    /***
     * 查询注册用户列表
     * @param pageable   分页信息
     * @param parameters 参数信息
     */
    Page<MutableUser> getAllMutableUsers(Page<MutableUser> pageable, Map<String, Object> parameters);

    /***
     * 根据关键字模糊查询用户列表
     *
     * @param key 关键字
     */
    List<MutableUser> getAllMutableUsersByKey(String key);

    /**
     * 保存用户
     *
     * @param user     保存对象
     * @param operator 当前操作人
     */
    String addMutableUser(MutableUser user, LoginUser operator);

    /***
     * 更新用户
     *
     * @param user      更新对象
     * @param operator  当前操作人
     */
    void updateMutableUser(MutableUser user, LoginUser operator);

    /***
     * 根据用户名删除用户
     * @param operator
     * @param username 用户名
     */
    void deleteUser(LoginUser operator, String username);

    /***
     * 检查用户名是否已经存在
     * @param username 用户名
     * @return boolean
     */
    boolean checkUserName(String username);

    /***
     * 修改用户密码
     * @param username      用户名
     * @param oldpassword   原密码
     * @param newpassword   新密码
     */
    void updateUserPassword(String username, String oldpassword, String newpassword);

    /**
     * 重置密码
     *
     * @param pwdParameter
     * @return java.lang.String
     */
    String resetUserPassword(ResetPwdDTO pwdParameter);

    /**
     * 禁用/启用用户帐号
     *
     * @param operator
     * @param type
     * @param username
     */
    void prohibitUser(LoginUser operator, Integer type, String username);

    /**
     * 解锁用户
     *
     * @param username
     * @param operator
     */
    void unlockUser(String username, LoginUser operator);

    /**
     * 根据组织编号查询用户列表
     *
     * @param orgid  组织编号
     * @param roleid 角色编号
     * @return
     */
    List<String> getUidsByOrg(String orgid, String roleid);

    /**
     * 获取用户的权限列表
     *
     * @param operator
     * @param source
     */
    Set<String> getPermissions(LoginUser operator, String source);

    /**
     * 批量保存用户的权限
     *
     * @param operator
     * @param target
     * @param permissions
     * @return void
     */
    void batchSavePermissions(LoginUser operator, String target, Set<String> permissions);

    /**
     * 批量保存用户的权限
     *
     * @param operator    当前用户
     * @param source      权限来源
     * @param target      复制对象
     * @param permissions 权限
     */
    void batchSavePermissions(LoginUser operator, String source, String target, Set<String> permissions);

    /**
     * 批量更新用户的权限
     *
     * @param operator    当前用户
     * @param source      权限来源
     * @param target      复制对象
     * @param permissions 权限
     *
     */
    void batchUpdatePermissions(LoginUser operator, String source, String target, Set<String> permissions);

    /**
     * 查询用户所有权限
     *
     * @param username
     * @param mode
     */
    List<Permission> getUserPermissions(String username, String mode);

    /**
     * 获取所有用户下拉数据
     *
     * @param operator
     * @param orgIds
     * @return
     */
    List<SimpleUser> getAllSimpleUser(LoginUser operator, List<String> orgIds);

    /**
     * 当前用户组织机构数据权限
     *
     * @param orgid    orgid
     * @param operator operator
     * @return 返回组织机构集合
     */
    Set<String> getSubOrgIds(String orgid, LoginUser operator);

    /**
     * 根据用户名称列表查询用户的扩展属性
     *
     * @param names 用户名称列表
     * @return Map<String, MutableUser>
     */
    Map<String, UserInfoEntity> getUserProps(Set<String> names);

    /**
     * 获取上级领导集合
     *
     * @param uid  uid
     * @param rank 机构层级
     * @return
     */
    List<String> getSuperiors(String uid, Integer rank);

    /**
     * 增加用户新增字段信息
     *
     * @param personal 字段map
     * @param username 用户id
     */
    void addUserFields(Map<String, String> personal, String username);

    /***
     * 根据租户ID查询租户管理员列表
     *
     * @param tenantId 租户ID
     */
    List<MutableUser> getAllMutableUsersByTenantId(String tenantId);

    /***
     * 更新租户管理员用户
     *
     * @param user      更新对象
     * @param operator  当前操作人
     */
    void updateTenantUser(MutableUser user, LoginUser operator);

    /**
     * 导出用户列表
     *
     * @param pageable   分页
     * @param parameters 查询参数
     */
    void exportMutableUsers(Page<MutableUser> pageable, Map<String, Object> parameters);
}
