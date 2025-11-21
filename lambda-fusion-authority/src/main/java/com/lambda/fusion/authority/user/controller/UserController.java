package com.lambda.fusion.authority.user.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.authority.tenant.service.TenantAuthorizeManager;
import com.lambda.fusion.authority.user.model.*;
import com.lambda.fusion.authority.user.model.LoginUserInfo;
import com.lambda.fusion.authority.user.model.User;
import com.lambda.fusion.authority.user.model.Permission;
import com.lambda.fusion.authority.user.model.SimpleUser;
import com.lambda.fusion.authority.user.model.VerifyCode;
import com.lambda.fusion.authority.user.optimizer.UserQueryOptimizer;
import com.lambda.fusion.authority.user.service.UserCenterService;
import com.lambda.fusion.authority.user.service.UserInfoService;
import com.lambda.fusion.authority.user.service.UserService;
import com.lambda.fusion.core.tree.builder.TreeBuilder;
import com.lambda.fusion.core.user.Operator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
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
@RequestMapping({"/authority/users"})
@Tag(name = "用户管理")
@RequiredArgsConstructor
public class UserController {

    private final OrganizationService organizationService;
    private final UserService userService;
    private final UserQueryOptimizer userQueryOptimizer;
    private final UserCenterService userCenterService;
    private final UserInfoService userInfoService;
    private TenantAuthorizeManager tenantAuthorizeManager;

    @Autowired(required = false)
    public void setTenantAuthorizeManager(TenantAuthorizeManager tenantAuthorizeManager) {
        this.tenantAuthorizeManager = tenantAuthorizeManager;
    }

    @GetMapping({"/page", "/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "分页查询所有用户列表")
    public Page<User> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid UserQuery queryDTO) {
        if (number != null) {
            queryDTO.setPageNum(number);
        }
        if (size != null) {
            queryDTO.setPageSize(size);
        }
        Map<String, Object> parameters = userQueryOptimizer.getMutableUsersQueryParameters(queryDTO);
        return userService.getUsers(queryDTO.getPage(), parameters);
    }

    @GetMapping(value = "/{username}/check")
    @Operation(summary = "检查用户名是否存在")
    public Boolean checkName(
            @Parameter(description = "用户名", required = true) @PathVariable("username") String username) {
        return userService.checkUserName(username.trim());
    }

    @GetMapping(value = "/{username}")
    @Operation(summary = "查询用户信息")
    public User get(
            @Parameter(description = "用户名", required = true) @PathVariable("username") String username) {
        return userService.getUserByUsername(username);
    }

    @GetMapping("/search")
    @Operation(summary = "根据关键字模糊查询用户列表")
    public List<User> search(
            @Parameter(description = "关键字", required = true) @RequestParam("key") String key) {
        return userService.getUsersByKey(key);
    }

    @GetMapping()
    @Operation(summary = "查询用户下拉列表")
    public List<SimpleUser> allUser(@RequestParam(required = false, defaultValue = "false") Boolean isAll) {
        LoginUser operator = OperatorUtils.getOperator();
        List<String> orgIds =
                isAll != null && isAll ? Collections.emptyList() : organizationService.getSubordinateOrgIds(operator);
        return userService.getAllSimpleUser(operator, orgIds);
    }

    @GetMapping("/my")
    @Operation(summary = "查询当前用户的详细信息")
    public User getUserById() {
        LoginUser operator = OperatorUtils.getOperator();
        return userService.getUserByUsername(operator.getName());
    }

    @GetMapping("/authority/{authority}")
    @Operation(summary = "根据角色查询用户名", description = "根据角色查询用户")
    public List<String> getNamesByAuthority(@PathVariable String authority) {
        LoginUser operator = OperatorUtils.getOperator();
        return userService.getUserNamesByAuthority(operator.getOrgId(), authority);
    }

    @GetMapping("/currentUser/info")
    @Operation(summary = "获取当前登陆用户详细信息")
    public LoginUserInfo getCurrentUserInfo() {
        Operator operator = OperatorUtils.getLoginUser(Operator.class);
        LoginUserInfo loginUserInfo = new LoginUserInfo();
        if (operator != null) {
            User user = userService.getCurrentUser(operator);
            BeanUtils.copyProperties(Objects.requireNonNullElse(user, operator), loginUserInfo);
        }
        return loginUserInfo;
    }

    @PostMapping
    @Operation(summary = "新增用户信息")
    public User add(
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody CreateUser createUser) {
        LoginUser operator = OperatorUtils.getOperator();
        userService.addUser(createUser, operator);
        User user = userService.getUserByUsername(createUser.getUserid());
        if (MapUtils.isNotEmpty(createUser.getPersonal())) {
            userService.addUserFields(createUser.getPersonal(), createUser.getUserid());
        }
        if (tenantAuthorizeManager != null) {
            // 添加租户用户
        }
        return user;
    }

    @PutMapping(value = "/{username}")
    @Operation(summary = "更新用户信息")
    public User update(
            @Parameter(description = "用户名称", required = true) @PathVariable("username") String username,
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody UpdateUser updateUser) {
        LoginUser operator = OperatorUtils.getOperator();
        updateUser.setUsername(username);
        userService.updateUser(updateUser, operator);
        return userService.getUserByUsername(username);
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
            @Parameter(description = "修改密码参数", required = true) @RequestBody ResetPassword resetPassword) {
        LoginUser operator = OperatorUtils.getOperator();
        String oldPassword = resetPassword.getOldPassword();
        String newPassword = resetPassword.getNewPassword();
        Assert.notNull(oldPassword, "原密码不能为空！");
        Assert.notNull(newPassword, "新密码不能为空！");
        resetPassword.setUsername(operator.getName());
        userService.updateUserPassword(operator.getName(), oldPassword, newPassword);
    }

    @PutMapping("/password/reset")
    @Operation(summary = "重置用户密码", description = "主要由用户管理员使用")
    public void resetUserPassword(
            @Parameter(description = "重置密码参数", required = true) @RequestBody ResetPassword resetPassword) {
        String username = resetPassword.getUsername();
        Assert.notNull(username, "username is not empty");
        String password = userService.resetUserPassword(resetPassword);
        if (tenantAuthorizeManager != null) {
            resetPassword.setNewPassword(password);
            tenantAuthorizeManager.resetPassword(resetPassword);
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
        return TreeBuilder.build(permissions);
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

    @PatchMapping(value = "/unbind/{username}/{type}")
    @Operation(summary = "解除第三方绑定")
    public void unbind(
            @Parameter(description = "用户编号", required = true) @PathVariable("username") String username,
            @Parameter(description = "第三方绑定类型(1、钉钉；2、微信)", required = true, schema = @Schema(defaultValue = "1"))
                    @PathVariable("type")
                    String type) {
        LoginUser operator = OperatorUtils.getOperator();
        userInfoService.unbindUserInfo(operator, type, username);
    }

    @PutMapping(value = "/update/mobile")
    @Operation(summary = "更新用户手机号")
    public void updateMobile(
            @Parameter(description = "手机号", required = true) @RequestParam("mobile") String mobile,
            @Parameter(description = "验证码", required = true) @RequestParam("verifyCode") String verifyCode) {
        LoginUser operator = OperatorUtils.getOperator();
        userCenterService.updateMobile(operator.getName(), mobile, verifyCode);
    }

    @PutMapping(value = "/update/email")
    @Operation(summary = "更新用户邮箱")
    public void updateEmail(
            @Parameter(description = "邮箱", required = true) @RequestParam("email") String email,
            @Parameter(description = "验证码", required = true) @RequestParam("verifyCode") String verifyCode) {
        LoginUser operator = OperatorUtils.getOperator();
        userCenterService.updateEmail(operator.getName(), email, verifyCode);
    }

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

    @PostMapping(value = "/send/mobile/code")
    @Operation(summary = "发送手机验证码")
    public VerifyCode sendMobileVerifyCodeStore(
            @Parameter(description = "手机号", required = true) @RequestParam("mobile") String mobile) {
        LoginUser operator = OperatorUtils.getOperator();
        return userCenterService.sendMobileVerifyCodeStore(operator.getName(), mobile);
    }

    @GetMapping("/tenant")
    @Operation(summary = "根据租户ID查询租户管理员")
    public List<User> tenant(
            @Parameter(description = "租户ID", required = true) @RequestParam("tenantId") String tenantId) {
        return userService.getAllMutableUsersByTenantId(tenantId);
    }

    @PutMapping(value = "/tenant/{username}")
    @Operation(summary = "更新租户管理员用户信息")
    public User updateTenantUser(
            @Parameter(description = "用户名称", required = true) @PathVariable("username") String username,
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody User user) {
        LoginUser operator = OperatorUtils.getOperator();
        user.setUsername(username);
        User copy = BeanUtil.toBean(user, User.class);
        userService.updateTenantUser(user, operator);
        User updated = userService.getUserByUsername(username);

        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.updateUser(copy);
        }

        return updated;
    }
}
