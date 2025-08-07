package com.lambda.fusion.auth.user.controller;

import static com.lambda.fusion.core.utils.ParameterUtils.fuzzyQuery;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.auth.organization.service.OrganizationService;
import com.lambda.fusion.auth.role.service.RoleService;
import com.lambda.fusion.auth.tenant.service.TenantAuthorizeManager;
import com.lambda.fusion.auth.user.domain.*;
import com.lambda.fusion.auth.user.service.UserCenterService;
import com.lambda.fusion.auth.user.service.UserInfoService;
import com.lambda.fusion.auth.user.service.UserService;
import com.lambda.fusion.autoconfig.AuthorizeConstants;
import com.lambda.fusion.core.tree.TreeFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户信息Api
 *
 */
@Slf4j
@RestController
@RequestMapping({"/authority/users", "/authority/users"})
@Tag(name = "用户管理")
public class UserController {
    private static final String ORGANS = "organs";

    @Resource
    private UserService userService;

    @Resource
    private RoleService roleService;

    @Autowired
    private UserCenterService userCenterService;

    @Resource
    private UserInfoService userInfoService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired(required = false)
    private TenantAuthorizeManager tenantAuthorizeManager;

    @GetMapping(value = {"/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(
            summary = "分页查询所有用户列表",
            parameters = {
                @Parameter(
                        name = "number",
                        description = "当前页码",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "1")),
                @Parameter(
                        name = "size",
                        description = "每页条数",
                        in = ParameterIn.PATH,
                        schema = @Schema(defaultValue = "20")),
                @Parameter(name = "username", description = "用户名称", in = ParameterIn.QUERY),
                @Parameter(name = "nickname", description = "用户昵称", in = ParameterIn.QUERY),
                @Parameter(name = "authority", description = "角色名称", in = ParameterIn.QUERY),
                @Parameter(name = "mobile", description = "电话号码", in = ParameterIn.QUERY),
                @Parameter(name = "email", description = "电子邮箱", in = ParameterIn.QUERY),
                @Parameter(name = "organizationId", description = "组织ID", in = ParameterIn.QUERY),
                @Parameter(
                        name = "subordinate",
                        description = "是否查询下级组织的人员",
                        in = ParameterIn.QUERY,
                        schema = @Schema(defaultValue = "true")),
                @Parameter(
                        name = "allocation",
                        description = "是否是分配人员接口调用",
                        in = ParameterIn.QUERY,
                        schema = @Schema(defaultValue = "false")),
                @Parameter(name = "personal", description = "新增查询字段", in = ParameterIn.QUERY),
                @Parameter(name = "isOnline", description = "是否在线", in = ParameterIn.QUERY),
                @Parameter(name = "isExport", description = "是否导出", in = ParameterIn.QUERY)
            })
    public Page<MutableUser> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String authority,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false, defaultValue = "true") boolean subordinate,
            @RequestParam(required = false) String personal,
            @RequestParam(required = false) Boolean isOnline,
            @RequestParam(required = false) Boolean isExport,
            @RequestParam(required = false) String exportColumns,
            HttpServletResponse response) {
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(11);
        LoginUser operator = OperatorUtils.getOperator();
        String tenantId = operator.getTenantId();
        parameters.put("username", username);
        //        parameters.put("dev", OperatorUtils.isDev(operator));
        //        parameters.put("admin", OperatorUtils.isAdmin(operator));
        parameters.put("uid", operator.getUsername());
        if (StringUtils.isNotBlank(email)) {
            parameters.put("email", fuzzyQuery(email));
        }
        if (StringUtils.isNotBlank(nickname)) {
            parameters.put("nickname", fuzzyQuery(nickname));
        }
        if (StringUtils.isNotBlank(mobile)) {
            parameters.put("mobile", fuzzyQuery(mobile));
        }
        if (StringUtils.isNotBlank(operator.getTenantId())) {
            parameters.put("tenant_id", tenantId);
        }
        if (StringUtils.isNotBlank(authority)) {
            parameters.put("authority", authority);
        }
        if (StringUtils.isNotBlank(personal)) {
            parameters.put("personal", personal);
        }
        if (isOnline != null) {
            parameters.put("isOnline", isOnline);
        }
        addOrgansParameter(organizationId, subordinate, parameters, true);
        Page<MutableUser> pageable = new Page<>(number, size);
        if (isExport != null && isExport) {
            parameters.put("exportColumns", exportColumns);
            userService.exportMutableUsers(pageable, parameters, response);
            return null;
        }
        return userService.getAllMutableUsers(pageable, parameters);
    }

    @GetMapping(value = "/{username}/check")
    @Operation(summary = "检查用户名是否存在")
    public Boolean checkName(
            @Parameter(description = "用户名", required = true) @PathVariable("username") String username) {
        return userService.checkUserName(username.trim());
    }

    @GetMapping(value = "/{username}")
    @Operation(summary = "查询用户信息")
    public MutableUser get(@Parameter(description = "用户名", required = true) @PathVariable("username") String username) {
        return userService.getMutableUserByUsername(username);
    }

    @GetMapping("/search")
    @Operation(summary = "根据关键字模糊查询用户列表")
    public List<MutableUser> search(@Parameter(description = "关键字", required = true) @RequestParam("key") String key) {
        return userService.getAllMutableUsersByKey(key);
    }

    @GetMapping()
    @Operation(summary = "查询用户下拉列表")
    public List<SimpleUser> allUser(@RequestParam(required = false, defaultValue = "false") Boolean isAll) {
        LoginUser operator = OperatorUtils.getOperator();
        List<String> organs =
                isAll != null && isAll ? Collections.emptyList() : organizationService.getSubordinateOrgIds(operator);
        return userService.getAllSimpleUser(operator, organs);
    }

    @GetMapping("/my")
    @Operation(summary = "查询当前用户的详细信息")
    public MutableUser getUserById() {
        LoginUser operator = OperatorUtils.getOperator();
        return userService.getMutableUserByUsername(operator.getUsername());
    }

    @GetMapping("/authority/{authority}")
    @Operation(summary = "根据角色查询用户名", description = "根据角色查询用户")
    public List<String> getUserNamesByAuthority(@PathVariable String authority) {
        LoginUser operator = OperatorUtils.getOperator();
        return userService.getUserNamesByAuthority(operator.getOrgId(), authority);
    }

    @GetMapping("/currentuser/info")
    @Operation(summary = "获取当前登陆用户详细信息")
    public LoginUserInfo getCurrentUserInfo() {
        LoginUser operator = OperatorUtils.getOperator();
        LoginUserInfo loginUserInfo = new LoginUserInfo();
        if (operator != null) {
            MutableUser mutableUserByUsername = userService.getCurrentMutableUser(operator);
            if (null != mutableUserByUsername) {
                BeanUtils.copyProperties(mutableUserByUsername, loginUserInfo);
            } else {
                BeanUtils.copyProperties(operator, loginUserInfo);
            }
        }
        return loginUserInfo;
    }

    @PostMapping
    @Operation(summary = "新增用户信息")
    public MutableUser add(
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody MutableUser mutableUser) {
        LoginUser operator = OperatorUtils.getOperator();
        // 去除username中前后可能存在的空格
        mutableUser.setUsername(mutableUser.getUsername().trim());
        MutableUser copy = BeanUtil.toBean(mutableUser, MutableUser.class);
        String password = userService.addMutableUser(mutableUser, operator);
        MutableUser user = userService.getMutableUserByUsername(mutableUser.getUsername());
        if (MapUtils.isNotEmpty(mutableUser.getPersonal())) {
            userService.addUserFields(mutableUser.getPersonal(), mutableUser.getUsername());
        }
        user.setPassword(password);
        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.addUser(copy);
        }

        return user;
    }

    @PutMapping(value = "/{username}")
    @Operation(summary = "更新用户信息")
    public MutableUser update(
            @Parameter(description = "用户名称", required = true) @PathVariable("username") String username,
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody MutableUser mutableUser) {
        LoginUser operator = OperatorUtils.getOperator();
        mutableUser.setUsername(username);
        userService.updateMutableUser(mutableUser, operator);
        return userService.getMutableUserByUsername(username);
    }

    @DeleteMapping(value = "/{username}")
    @Operation(summary = "删除用户信息")
    public void delete(@Parameter(description = "用户名", required = true) @PathVariable("username") String username) {
        LoginUser operator = OperatorUtils.getOperator();
        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.deleteUser(username);
        }
        userService.deleteUser(operator, username);
    }

    @PutMapping("/password/edit")
    @Operation(summary = "修改用户密码", description = "用于用户自己修改密码")
    public void updateUserPassword(
            @Parameter(description = "修改密码参数", required = true) @RequestBody ResetPwdParameter resetPwdParameter) {
        LoginUser operator = OperatorUtils.getOperator();
        String oldpassword = resetPwdParameter.getOldPassword();
        String newpassword = resetPwdParameter.getNewPassword();
        Assert.notNull(oldpassword, "lambda.authority.user.originalpassword.notempty");
        Assert.notNull(newpassword, "lambda.authority.user.newpassword.notempty");
        resetPwdParameter.setUsername(operator.getUsername());
        userService.updateUserPassword(operator.getUsername(), oldpassword, newpassword);
    }

    @PutMapping("/password/reset")
    @Operation(summary = "重置用户密码", description = "主要由用户管理员使用")
    public void resetUserPassword(
            @Parameter(description = "重置密码参数", required = true) @RequestBody ResetPwdParameter resetPwdParameter) {
        String username = resetPwdParameter.getUsername();
        Assert.notNull(username, AuthorizeConstants.USER_NAME_NOT_EMPTY);
        String password = userService.resetUserPassword(resetPwdParameter);
        if (tenantAuthorizeManager != null) {
            // 新密码作为租户主库管理员的密码，使数据保持一致
            resetPwdParameter.setNewPassword(password);
            tenantAuthorizeManager.resetPassword(resetPwdParameter);
        }
    }

    @PatchMapping("/{username}/disabled")
    @Operation(summary = "禁用用户")
    public void disabled(@Parameter(description = "用户名称", required = true) @PathVariable("username") String username) {
        LoginUser operator = OperatorUtils.getOperator();
        userService.prohibitUser(operator, 0, username);

        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.prohibitUser(0, username);
        }
    }

    @PatchMapping("/{username}/enabled")
    @Operation(summary = "启用用户")
    public void enabled(@Parameter(description = "用户名称", required = true) @PathVariable("username") String username) {
        LoginUser operator = OperatorUtils.getOperator();
        userService.prohibitUser(operator, 1, username);

        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.prohibitUser(1, username);
        }
    }

    @PatchMapping("/{username}/unlock")
    @Operation(summary = "解锁用户")
    public void unlock(@Parameter(description = "用户名称", required = true) @PathVariable("username") String username) {
        LoginUser operator = OperatorUtils.getOperator();
        userService.unlockUser(username, operator);
    }

    @GetMapping("/permission/{username}")
    @Operation(summary = "查询用户所有权限")
    public List<Permission> userPermissions(
            @Parameter(description = "用户ID", required = true) @PathVariable("username") String username,
            @RequestParam(required = false) String mode) {
        List<Permission> permissions = userService.getUserPermissions(username, mode);
        return TreeFactory.build(permissions);
    }

    @PutMapping("/permission/copy")
    @Operation(summary = "用户权限复制")
    public void permissionCopy(
            @Parameter(description = "权限来源", required = true) @RequestParam("source") String source,
            @Parameter(description = "复制对象", required = true) @RequestParam("target") String target) {
        LoginUser operator = OperatorUtils.getOperator();
        Set<String> permissions1 = userService.getPermissions(operator, source);
        Set<String> permissions2 = userService.getPermissions(operator, target);
        // 两个权限的差集，1中有而2中没有的
        Set<String> insertPermissions = Sets.difference(permissions1, permissions2);
        // 两个权限的交集，1中有并且2中也有的
        Set<String> updatePermissions = Sets.intersection(permissions1, permissions2);
        // 保存差集权限
        if (CollectionUtils.isNotEmpty(insertPermissions)) {
            userService.batchSavePermissions(operator, source, target, insertPermissions);
        }
        // 更新交集权限
        if (CollectionUtils.isNotEmpty(updatePermissions)) {
            userService.batchUpdatePermissions(operator, source, target, updatePermissions);
        }
    }

    @GetMapping(value = {"/list"})
    @Operation(
            summary = "不分页查询所有用户列表",
            parameters = {
                @Parameter(name = "username", description = "用户名称", in = ParameterIn.QUERY),
                @Parameter(name = "nickname", description = "用户昵称", in = ParameterIn.QUERY),
                @Parameter(name = "authority", description = "角色名称", in = ParameterIn.QUERY),
                @Parameter(name = "mobile", description = "电话号码", in = ParameterIn.QUERY),
                @Parameter(name = "email", description = "电子邮箱", in = ParameterIn.QUERY),
                @Parameter(name = "organizationId", description = "组织ID", in = ParameterIn.QUERY),
                @Parameter(
                        name = "subordinate",
                        description = "是否查询下级组织的人员",
                        in = ParameterIn.QUERY,
                        schema = @Schema(defaultValue = "true")),
                @Parameter(
                        name = "allocation",
                        description = "是否是分配人员接口调用",
                        in = ParameterIn.QUERY,
                        schema = @Schema(defaultValue = "false")),
                @Parameter(
                        name = "dataRight",
                        description = "是否开启数据权限",
                        in = ParameterIn.QUERY,
                        schema = @Schema(defaultValue = "true")),
                @Parameter(name = "personal", description = "新增查询字段", in = ParameterIn.QUERY)
            })
    public List<MutableUser> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String authority,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false, defaultValue = "true") boolean subordinate,
            @RequestParam(required = false, defaultValue = "true") boolean dataRight,
            @RequestParam(required = false) String personal) {
        LoginUser operator = OperatorUtils.getOperator();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(11);
        String tenantId = operator.getTenantId();
        parameters.put("username", username);
        //        parameters.put("dev", OperatorUtils.isDev(operator));
        //        parameters.put("admin", OperatorUtils.isAdmin(operator));
        parameters.put("uid", operator.getUsername());
        if (StringUtils.isNotBlank(email)) {
            parameters.put("email", fuzzyQuery(email));
        }
        if (StringUtils.isNotBlank(nickname)) {
            parameters.put("nickname", fuzzyQuery(nickname));
        }
        if (StringUtils.isNotBlank(mobile)) {
            parameters.put("mobile", fuzzyQuery(mobile));
        }
        if (StringUtils.isNotBlank(tenantId)) {
            parameters.put("tenant_id", tenantId);
        }
        if (StringUtils.isNotBlank(authority)) {
            parameters.put("authority", authority);
        }
        if (StringUtils.isNotBlank(personal)) {
            parameters.put("personal", personal);
        }
        addOrgansParameter(organizationId, subordinate, parameters, dataRight);
        return userService.getAllMutableUsersNoPage(parameters);
    }

    @PatchMapping(value = "/unbind/{username}/{type}")
    @Operation(summary = "解除第三方绑定")
    public void unbind(
            @Parameter(description = "用户编号", required = true) @PathVariable("username") String username,
            @Parameter(description = "第三方绑定类型(1、钉钉；2、微信)", required = true, schema = @Schema(defaultValue = "1"))
                    @PathVariable("type")
                    String type) {
        LoginUser operator = OperatorUtils.getOperator();
        userInfoService.unbindUserInfo(operator, type, operator.getUsername());
    }

    @PutMapping(value = "/update/mobile")
    @Operation(summary = "更新用户手机号")
    public void updateMobile(
            @Parameter(description = "手机号", required = true) @RequestParam("mobile") String mobile,
            @Parameter(description = "验证码", required = true) @RequestParam("verifyCode") String verifyCode) {
        LoginUser operator = OperatorUtils.getOperator();
        userCenterService.updateMobile(operator.getUsername(), mobile, verifyCode);
    }

    @PutMapping(value = "/update/email")
    @Operation(summary = "更新用户邮箱")
    public void updateEmail(
            @Parameter(description = "邮箱", required = true) @RequestParam("email") String email,
            @Parameter(description = "验证码", required = true) @RequestParam("verifyCode") String verifyCode) {
        LoginUser operator = OperatorUtils.getOperator();
        userCenterService.updateEmail(operator.getUsername(), email, verifyCode);
    }

    @PostMapping(value = "/update/info")
    @Operation(
            summary = "更新个人信息",
            parameters = {
                @Parameter(name = "nickname", description = "昵称", required = true),
                @Parameter(name = "email", description = "邮箱", required = true),
                @Parameter(name = "personal", description = "新增字段")
            })
    public MutableUser updateInfo(
            MultipartFile avatar,
            @RequestParam("nickname") String nickname,
            @RequestParam("email") String email,
            @RequestParam("personal") String personal) {
        LoginUser operator = OperatorUtils.getOperator();
        RestUserInfoParameter parameter = new RestUserInfoParameter();
        parameter.setEmail(email);
        parameter.setNickname(nickname);
        parameter.setUsername(operator.getUsername());
        parameter.setPersonal(personal);
        if (avatar != null) {
            log.info("file: {}", avatar);
            //
        }
        return userCenterService.updateInfo(parameter);
    }

    @PostMapping(value = "/send/mobile/code")
    @Operation(summary = "发送手机验证码")
    public RestVerifyCodeInfo sendMobileVerifyCodeStore(
            @Parameter(description = "手机号", required = true) @RequestParam("mobile") String mobile) {
        LoginUser operator = OperatorUtils.getOperator();
        return userCenterService.sendMobileVerifyCodeStore(operator.getUsername(), mobile);
    }

    @GetMapping("/tenant")
    @Operation(summary = "根据租户ID查询租户管理员")
    public List<MutableUser> tenant(
            @Parameter(description = "租户ID", required = true) @RequestParam("tenantId") String tenantId) {
        return userService.getAllMutableUsersByTenantId(tenantId);
    }

    @PutMapping(value = "/tenant/{username}")
    @Operation(summary = "更新租户管理员用户信息")
    public MutableUser updateTenantUser(
            @Parameter(description = "用户名称", required = true) @PathVariable("username") String username,
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody MutableUser mutableUser) {
        LoginUser operator = OperatorUtils.getOperator();
        mutableUser.setUsername(username);
        MutableUser copy = BeanUtil.toBean(mutableUser, MutableUser.class);
        userService.updateTenantUser(mutableUser, operator);
        MutableUser updated = userService.getMutableUserByUsername(username);

        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.updateUser(copy);
        }

        return updated;
    }

    /**
     * 增加组织机构参数
     *
     * @param organizationId 组织id
     * @param subordinate    是否包含下级机构
     * @param parameters     参数信息
     * @param dataRight      是否包含数据权限
     */
    private void addOrgansParameter(
            String organizationId, boolean subordinate, Map<String, Object> parameters, boolean dataRight) {
        LoginUser operator = OperatorUtils.getOperator();
        if (subordinate || StringUtils.isBlank(organizationId)) {
            if (!dataRight) {
                if (StringUtils.isNotBlank(organizationId)) {
                    List<String> subOrgans = organizationService.getChildrenById(organizationId);
                    parameters.put(ORGANS, Sets.newHashSet(subOrgans));
                }
            } else {
                parameters.put(ORGANS, userService.getSubOrgans(organizationId, operator));
            }
        } else {
            List<String> list = new ArrayList<>();
            list.add(organizationId);
            parameters.put(ORGANS, list);
        }
    }
}
