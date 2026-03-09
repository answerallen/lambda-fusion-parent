package com.lambda.fusion.authority.controller;

import static com.lambda.fusion.core.FusionConstants.AT;
import static com.lambda.fusion.core.FusionConstants.ROLE_TENANT;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.helper.UserQueryHelper;
import com.lambda.fusion.authority.manager.TenantAuthorizeManager;
import com.lambda.fusion.authority.model.role.SimpleRole;
import com.lambda.fusion.authority.model.user.*;
import com.lambda.fusion.authority.service.OrganizationService;
import com.lambda.fusion.authority.service.UserCenterService;
import com.lambda.fusion.authority.service.UserInfoService;
import com.lambda.fusion.authority.service.UserService;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户信息Api
 *
 */
@Slf4j
@RestController
@RequestMapping({"/authority/users"})
@Tag(name = "用户管理")
@RequiredArgsConstructor
public class UserController {

    private final OrganizationService organizationService;
    private final UserService userService;
    private final UserQueryHelper userQueryHelper;
    private final UserCenterService userCenterService;
    private final UserInfoService userInfoService;
    private TenantAuthorizeManager tenantAuthorizeManager;

    @Autowired(required = false)
    public void setTenantAuthorizeManager(TenantAuthorizeManager tenantAuthorizeManager) {
        this.tenantAuthorizeManager = tenantAuthorizeManager;
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000066")
    @GetMapping({"", "/page", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "分页查询所有用户列表")
    public Page<User> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid UserQuery userQuery) {
        if (number != null) {
            userQuery.setPageNum(number);
        }
        if (size != null) {
            userQuery.setPageSize(size);
        }
        UserQueryContext userQueryContext = userQueryHelper.buildUserQueryContext(userQuery);
        return userService.getUsers(userQuery.getPage(), userQueryContext);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000067")
    @GetMapping(value = "/{username}/check")
    @Operation(summary = "检查用户名是否存在")
    public Boolean checkName(@PathVariable @Parameter(description = "用户名", required = true) String username) {
        return userService.checkUserName(StrUtil.trim(username));
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000068")
    @GetMapping(value = "/{username}")
    @Operation(summary = "查询用户信息")
    public User getUser(@PathVariable @Parameter(description = "用户名", required = true) String username) {
        return userService.getByUsername(StrUtil.trim(username));
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000069")
    @GetMapping("/search")
    @Operation(summary = "根据关键字模糊查询用户列表")
    public List<User> search(@Parameter(description = "关键字", required = true) @RequestParam("key") String key) {
        return userService.getUsersByKey(key);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000070")
    @GetMapping("/allUser")
    @Operation(summary = "查询用户下拉列表")
    public List<UserProfile> allUser(@RequestParam(required = false, defaultValue = "false") Boolean isAll) {
        UserDetails loginUser = SecurityUtils.getUser();
        List<String> orgIds =
                isAll != null && isAll ? Collections.emptyList() : organizationService.getSubOrganizations(loginUser);
        return userService.getUserProfiles(loginUser, orgIds);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000071")
    @GetMapping("/current")
    @Operation(summary = "查询当前用户的详细信息")
    public User getCurrent() {
        LoginUser operator = OperatorUtils.getOperator();
        return userService.getByUsername(operator.getName());
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000072")
    @GetMapping("/authority/{authority}")
    @Operation(summary = "根据角色查询用户名", description = "根据角色查询用户")
    public List<String> getNamesByAuthority(@PathVariable String authority) {
        LoginUser operator = OperatorUtils.getOperator();
        return userService.getUserNamesByAuthority(operator.getOrgId(), authority);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000073")
    @PostMapping
    @Operation(summary = "新增用户信息")
    public User add(@Parameter(description = "用户信息", required = true) @Valid @RequestBody CreateUser createUser) {
        userService.addUser(createUser, SecurityUtils.getUser());
        if (MapUtils.isNotEmpty(createUser.getPersonal())) {
            userService.addUserFields(createUser.getPersonal(), createUser.getUsername());
        }
        if (tenantAuthorizeManager != null) {
            log.info("添加租户用户");
        }
        return userService.getByUsername(createUser.getUsername());
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000074")
    @PutMapping(value = "/{username}")
    @Operation(summary = "更新用户信息")
    public User update(
            @PathVariable @Parameter(description = "用户名称", required = true) String username,
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody UpdateUser updateUser) {
        updateUser.setUsername(username);
        userService.updateUser(updateUser, SecurityUtils.getUser());
        return userService.getByUsername(username);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000075")
    @DeleteMapping(value = "/{username}")
    @Operation(summary = "删除用户信息")
    public void delete(@PathVariable @Parameter(description = "用户名", required = true) String username) {
        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.deleteUser(username);
        }
        userService.deleteUser(SecurityUtils.getUser(), username);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000076")
    @PutMapping("/password/edit")
    @Operation(summary = "修改用户密码", description = "用于用户自己修改密码")
    public void updateUserPassword(
            @Parameter(description = "修改密码参数", required = true) @RequestBody ResetPassword resetPassword) {
        LoginUser operator = OperatorUtils.getOperator();
        String oldPassword = resetPassword.getOldPassword();
        String newPassword = resetPassword.getNewPassword();
        if (oldPassword == null) {
            throw AuthorityBusinessException.invalidParameter("原密码不能为空");
        }
        if (newPassword == null) {
            throw AuthorityBusinessException.invalidParameter("新密码不能为空");
        }
        resetPassword.setUsername(operator.getName());
        userService.updateUserPassword(operator.getName(), oldPassword, newPassword);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000077")
    @PutMapping("/password/reset")
    @Operation(summary = "重置用户密码", description = "主要由用户管理员使用")
    public String resetUserPassword(
            @Parameter(description = "重置密码参数", required = true) @RequestBody ResetPassword resetPassword) {
        String username = resetPassword.getUsername();
        if (username == null) {
            throw AuthorityBusinessException.invalidParameter("username不能为空");
        }
        String password = userService.resetUserPassword(resetPassword);
        if (tenantAuthorizeManager != null) {
            resetPassword.setNewPassword(password);
            tenantAuthorizeManager.resetPassword(resetPassword);
        }
        return password;
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000078")
    @PatchMapping("/{username}/disabled")
    @Operation(summary = "禁用用户")
    public void disabled(@PathVariable @Parameter(description = "用户名称", required = true) String username) {
        userService.deactivateUser(SecurityUtils.getUser(), FusionConstants.DISABLED, username);

        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.prohibitUser(FusionConstants.DISABLED, username);
        }
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000079")
    @PatchMapping("/{username}/enabled")
    @Operation(summary = "启用用户")
    public void enabled(@PathVariable @Parameter(description = "用户名称", required = true) String username) {
        userService.deactivateUser(SecurityUtils.getUser(), FusionConstants.ENABLED, username);

        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.prohibitUser(FusionConstants.ENABLED, username);
        }
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000080")
    @PatchMapping("/{username}/unlock")
    @Operation(summary = "解锁用户")
    public void unlock(@PathVariable @Parameter(description = "用户名称", required = true) String username) {
        userService.unlockUser(username, SecurityUtils.getUser());
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000081")
    @GetMapping("/{username}/permission")
    @Operation(summary = "查询用户所有权限")
    public List<Permission> userPermissions(
            @PathVariable @Parameter(description = "用户ID", required = true) String username,
            @RequestParam(required = false) String mode) {
        List<Permission> permissions = userService.getUserPermissions(username, mode);
        return TreeBuilder.build(permissions);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000082")
    @PatchMapping(value = "/unbind/{username}/{type}")
    @Operation(summary = "解除第三方绑定")
    public void unbind(
            @PathVariable @Parameter(description = "用户编号", required = true) String username,
            @PathVariable
                    @Parameter(
                            description = "第三方绑定类型(1、钉钉；2、微信)",
                            required = true,
                            schema = @Schema(defaultValue = "1"))
                    String type) {
        LoginUser operator = OperatorUtils.getOperator();
        userInfoService.unbindUserInfo(operator, type, username);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000083")
    @PutMapping(value = "/update/mobile")
    @Operation(summary = "更新用户手机号")
    public void updateMobile(
            @Parameter(description = "手机号", required = true) @RequestParam("mobile") String mobile,
            @Parameter(description = "验证码", required = true) @RequestParam("verifyCode") String verifyCode) {
        LoginUser operator = OperatorUtils.getOperator();
        userCenterService.updateMobile(operator.getName(), mobile, verifyCode);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000084")
    @PutMapping(value = "/update/email")
    @Operation(summary = "更新用户邮箱")
    public void updateEmail(
            @Parameter(description = "邮箱", required = true) @RequestParam("email") String email,
            @Parameter(description = "验证码", required = true) @RequestParam("verifyCode") String verifyCode) {
        LoginUser operator = OperatorUtils.getOperator();
        userCenterService.updateEmail(operator.getName(), email, verifyCode);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000085")
    @PostMapping(value = "/update/info")
    @Operation(
            summary = "更新个人信息",
            parameters = {
                @Parameter(name = "nickname", description = "昵称", required = true),
                @Parameter(name = "email", description = "邮箱", required = true),
                @Parameter(name = "personal", description = "新增字段")
            })
    public User updateInfo(MultipartFile avatar, RestUserInfo restUserInfo) {
        LoginUser operator = OperatorUtils.getOperator();
        restUserInfo.setUsername(operator.getName());
        if (avatar != null) {
            log.info("file: {}", avatar);
        }
        return userCenterService.updateInfo(restUserInfo);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000086")
    @PostMapping(value = "/send/mobile/code")
    @Operation(summary = "发送手机验证码")
    public VerifyCode sendMobileVerifyCode(
            @Parameter(description = "手机号", required = true) @RequestParam("mobile") String mobile) {
        LoginUser operator = OperatorUtils.getOperator();
        return userCenterService.sendMobileVerifyCode(operator.getName(), mobile);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000087")
    @GetMapping("/tenant/{tenantId}/admins")
    @Operation(summary = "查询租户管理员")
    public List<User> tenantAdmins(@PathVariable @Parameter(description = "租户ID", required = true) String tenantId) {
        return userService.queryTenantAdmins(tenantId);
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000088")
    @PutMapping(value = "/tenant/{tenantId}/admins")
    @Operation(summary = "添加租户管理员用户信息")
    public User addTenantUser(
            @PathVariable @Parameter(description = "租户ID", required = true) String tenantId,
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody CreateTenantUser createTenantUser) {
        CreateUser createUser = ConvertUtils.convert(createTenantUser);
        createUser.setTenantId(tenantId);
        SimpleRole simpleRole = new SimpleRole(ROLE_TENANT + AT + tenantId);
        createUser.setAuthorities(List.of(simpleRole));
        userService.addUser(createUser, SecurityUtils.getUser());
        User updated = userService.getByUsername(createTenantUser.getUsername());
        if (tenantAuthorizeManager != null) {
            User copy = BeanUtil.toBean(updated, User.class);
            tenantAuthorizeManager.addUser(copy);
        }
        return updated;
    }

    @SaCheckPermission(orRole = FusionConstants.ROLE_DEV, value = "T1000000089")
    @PutMapping(value = "/tenant/{tenantId}/admins/{username}")
    @Operation(summary = "更新租户管理员用户信息")
    public User updateTenantUser(
            @PathVariable @Parameter(description = "租户ID", required = true) String tenantId,
            @PathVariable @Parameter(description = "用户名称", required = true) String username,
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody UpdateTenantUser updateTenantUser) {
        User user = ConvertUtils.convert(updateTenantUser);
        user.setTenantId(tenantId);
        user.setUsername(username);
        userService.updateTenantUser(user, SecurityUtils.getUser());
        User updated = userService.getByUsername(username);
        if (tenantAuthorizeManager != null) {
            User copy = BeanUtil.toBean(updated, User.class);
            tenantAuthorizeManager.updateUser(copy);
        }
        return updated;
    }
}
