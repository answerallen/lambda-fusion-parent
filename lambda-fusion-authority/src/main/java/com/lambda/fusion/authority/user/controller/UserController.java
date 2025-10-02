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
import com.lambda.fusion.authority.user.model.dto.ResetPwdDTO;
import com.lambda.fusion.authority.user.model.dto.UserCreateDTO;
import com.lambda.fusion.authority.user.model.dto.UserPageQueryDTO;
import com.lambda.fusion.authority.user.model.dto.UserUpdateDTO;
import com.lambda.fusion.authority.user.model.vo.LoginUserInfoVO;
import com.lambda.fusion.authority.user.model.vo.MutableUserVO;
import com.lambda.fusion.authority.user.optimizer.UserQueryOptimizer;
import com.lambda.fusion.authority.user.service.UserCenterService;
import com.lambda.fusion.authority.user.service.UserInfoService;
import com.lambda.fusion.authority.user.service.UserService;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.fusion.core.user.User;
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
    public Page<MutableUserVO> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid UserPageQueryDTO queryDTO) {
        if (number != null) {
            queryDTO.setPageNum(number);
        }
        if (size != null) {
            queryDTO.setPageSize(size);
        }
        Map<String, Object> parameters = userQueryOptimizer.getMutableUsersQueryParameters(queryDTO);
        return userService.getAllMutableUsers(queryDTO.getPage(), parameters);
    }

    @GetMapping(value = "/{username}/check")
    @Operation(summary = "检查用户名是否存在")
    public Boolean checkName(
            @Parameter(description = "用户名", required = true) @PathVariable("username") String username) {
        return userService.checkUserName(username.trim());
    }

    @GetMapping(value = "/{username}")
    @Operation(summary = "查询用户信息")
    public MutableUserVO get(
            @Parameter(description = "用户名", required = true) @PathVariable("username") String username) {
        return userService.getMutableUserByUsername(username);
    }

    @GetMapping("/search")
    @Operation(summary = "根据关键字模糊查询用户列表")
    public List<MutableUserVO> search(
            @Parameter(description = "关键字", required = true) @RequestParam("key") String key) {
        return userService.getAllMutableUsersByKey(key);
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
    public MutableUserVO getUserById() {
        LoginUser operator = OperatorUtils.getOperator();
        return userService.getMutableUserByUsername(operator.getName());
    }

    @GetMapping("/authority/{authority}")
    @Operation(summary = "根据角色查询用户名", description = "根据角色查询用户")
    public List<String> getNamesByAuthority(@PathVariable String authority) {
        LoginUser operator = OperatorUtils.getOperator();
        return userService.getUserNamesByAuthority(operator.getOrgId(), authority);
    }

    @GetMapping("/currentUser/info")
    @Operation(summary = "获取当前登陆用户详细信息")
    public LoginUserInfoVO getCurrentUserInfo() {
        User operator = OperatorUtils.getLoginUser(User.class);
        LoginUserInfoVO loginUserInfo = new LoginUserInfoVO();
        if (operator != null) {
            MutableUserVO mutableUser = userService.getCurrentMutableUser(operator);
            BeanUtils.copyProperties(Objects.requireNonNullElse(mutableUser, operator), loginUserInfo);
        }
        return loginUserInfo;
    }

    @PostMapping
    @Operation(summary = "新增用户信息")
    public MutableUserVO add(
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody UserCreateDTO userCreateDTO) {
        LoginUser operator = OperatorUtils.getOperator();
        userService.addUser(userCreateDTO, operator);
        MutableUserVO user = userService.getMutableUserByUsername(userCreateDTO.getUserid());
        if (MapUtils.isNotEmpty(userCreateDTO.getPersonal())) {
            userService.addUserFields(userCreateDTO.getPersonal(), userCreateDTO.getUserid());
        }
        if (tenantAuthorizeManager != null) {
            // 添加租户用户
        }
        return user;
    }

    @PutMapping(value = "/{username}")
    @Operation(summary = "更新用户信息")
    public MutableUserVO update(
            @Parameter(description = "用户名称", required = true) @PathVariable("username") String username,
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody UserUpdateDTO userUpdateDTO) {
        LoginUser operator = OperatorUtils.getOperator();
        userUpdateDTO.setUsername(username);
        userService.updateUser(userUpdateDTO, operator);
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
            @Parameter(description = "修改密码参数", required = true) @RequestBody ResetPwdDTO resetPwdDTO) {
        LoginUser operator = OperatorUtils.getOperator();
        String oldPassword = resetPwdDTO.getOldPassword();
        String newPassword = resetPwdDTO.getNewPassword();
        Assert.notNull(oldPassword, "原密码不能为空！");
        Assert.notNull(newPassword, "新密码不能为空！");
        resetPwdDTO.setUsername(operator.getName());
        userService.updateUserPassword(operator.getName(), oldPassword, newPassword);
    }

    @PutMapping("/password/reset")
    @Operation(summary = "重置用户密码", description = "主要由用户管理员使用")
    public void resetUserPassword(
            @Parameter(description = "重置密码参数", required = true) @RequestBody ResetPwdDTO resetPwdDTO) {
        String username = resetPwdDTO.getUsername();
        Assert.notNull(username, "username is not empty");
        String password = userService.resetUserPassword(resetPwdDTO);
        if (tenantAuthorizeManager != null) {
            resetPwdDTO.setNewPassword(password);
            tenantAuthorizeManager.resetPassword(resetPwdDTO);
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
    public MutableUserVO updateInfo(
            MultipartFile avatar,
            @RequestParam("nickname") String nickname,
            @RequestParam("email") String email,
            @RequestParam("personal") String personal) {
        LoginUser operator = OperatorUtils.getOperator();
        RestUserInfoParameter parameter = new RestUserInfoParameter();
        parameter.setEmail(email);
        parameter.setNickname(nickname);
        parameter.setUsername(operator.getName());
        parameter.setPersonal(personal);
        if (avatar != null) {
            log.info("file: {}", avatar);
        }
        return userCenterService.updateInfo(parameter);
    }

    @PostMapping(value = "/send/mobile/code")
    @Operation(summary = "发送手机验证码")
    public RestVerifyCodeInfo sendMobileVerifyCodeStore(
            @Parameter(description = "手机号", required = true) @RequestParam("mobile") String mobile) {
        LoginUser operator = OperatorUtils.getOperator();
        return userCenterService.sendMobileVerifyCodeStore(operator.getName(), mobile);
    }

    @GetMapping("/tenant")
    @Operation(summary = "根据租户ID查询租户管理员")
    public List<MutableUserVO> tenant(
            @Parameter(description = "租户ID", required = true) @RequestParam("tenantId") String tenantId) {
        return userService.getAllMutableUsersByTenantId(tenantId);
    }

    @PutMapping(value = "/tenant/{username}")
    @Operation(summary = "更新租户管理员用户信息")
    public MutableUserVO updateTenantUser(
            @Parameter(description = "用户名称", required = true) @PathVariable("username") String username,
            @Parameter(description = "用户信息", required = true) @Valid @RequestBody MutableUserVO mutableUser) {
        LoginUser operator = OperatorUtils.getOperator();
        mutableUser.setUsername(username);
        MutableUserVO copy = BeanUtil.toBean(mutableUser, MutableUserVO.class);
        userService.updateTenantUser(mutableUser, operator);
        MutableUserVO updated = userService.getMutableUserByUsername(username);

        if (tenantAuthorizeManager != null) {
            tenantAuthorizeManager.updateUser(copy);
        }

        return updated;
    }
}
