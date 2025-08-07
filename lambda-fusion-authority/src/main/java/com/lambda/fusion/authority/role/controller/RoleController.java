package com.lambda.fusion.authority.role.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.role.bean.*;
import com.lambda.fusion.authority.role.service.InternalRoleService;
import com.lambda.fusion.authority.role.service.RoleService;
import com.lambda.fusion.authority.tenant.service.TenantAuthorizeManager;
import com.lambda.fusion.authority.user.domain.MutableUser;
import com.lambda.fusion.authority.user.service.UserService;
import com.lambda.fusion.autoconfig.AuthorizeConstants;
import com.lambda.fusion.core.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户角色API
 */
@RestController
@RequestMapping({"/authority/roles", "/authority/roles"})
@Tag(name = "角色管理")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserService userService;

    @Autowired
    private InternalRoleService internalRoleService;

    @Autowired(required = false)
    private TenantAuthorizeManager tenantResourceManager;

    @GetMapping
    @SaCheckLogin
    @Operation(description = "获取所有角色列表", summary = "获取所有角色列表")
    public List<MutableRole> list() {
        LoginUser operator = OperatorUtils.getOperator();
        return roleService.getAllRoles(operator);
    }

    @Operation(description = "分组列表", summary = "分组列表")
    @GetMapping("/group")
    public List<GroupVo> listGroups() {
        LoginUser user = OperatorUtils.getOperator();
        return roleService.listGroups(user);
    }

    @GetMapping("/group/role")
    @Operation(
            description = "获取所有角色分组列表",
            summary = "获取所有角色分组列表",
            parameters = {@Parameter(name = "tenant_id", description = "租户id")})
    public List<GroupRoleVo> groupRole(String tenantId) {
        LoginUser operator = OperatorUtils.getOperator();
        return roleService.getAllGroupRoles(operator, tenantId);
    }

    @GetMapping({"/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(
            description = "分页查询所有角色列表",
            summary = "分页查询所有角色列表",
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
                @Parameter(name = "alias", description = "角色别名"),
                @Parameter(name = "groupId", description = "分组ID")
            })
    public Page<MutableRole> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            String alias,
            String groupId) {
        LoginUser operator = OperatorUtils.getOperator();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(4);
        if (ObjectUtil.isNotNull(groupId)) {
            parameters.put("groupId", groupId);
        }
        if (StringUtils.isNotBlank(alias)) {
            parameters.put("alias", wrapParameter(alias));
        }
        Set<String> excludes = Sets.newHashSet();
        excludes.add(Constants.ROLE_USER);
        excludes.add(Constants.ROLE_HMAC);
        excludes.add(Constants.ROLE_DEV);
        excludes.add(Constants.ROLE_SYSTEM);
        excludes.add(Constants.ROLE_TENANT);
        //        if (!OperatorUtils.isDev(operator)) {
        //            excludes.add(Constants.ROLE_ADMIN);
        //        }
        Set<String> queryExclude = internalRoleService.queryExclude(operator);
        excludes.addAll(queryExclude);
        parameters.put("excludes", excludes);
        String tenantId = operator.getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            parameters.put("tenant_id", tenantId);
        }
        return roleService.getAllRoles(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(), parameters);
    }

    /**
     * 使用%包裹模糊搜索参数
     *
     * @param param
     * @return
     */
    private String wrapParameter(String param) {
        return StringUtils.isNotBlank(param) ? "%" + param + "%" : null;
    }

    @GetMapping("/check/{authority}")
    @Operation(description = "检查角色名称是否重复", summary = "检查角色名称是否重复")
    public Object check(@Parameter(description = "角色名称", required = true) @PathVariable String authority) {
        Map<String, Object> result = new HashMap<>(1);
        result.put("state", roleService.hasExists(authority));
        return result;
    }

    @GetMapping("/{authority}")
    @Operation(description = "查询角色信息", summary = "查询角色信息")
    public MutableRole update(@Parameter(description = "角色名称", required = true) @PathVariable String authority) {
        Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
        return roleService.getRoleByAuthority(authority);
    }

    @PostMapping
    @Operation(description = "新增角色信息", summary = "新增角色信息")
    public MutableRole add(@Parameter(description = "角色信息", required = true) @RequestBody MutableRole mutableRole) {
        LoginUser operator = OperatorUtils.getOperator();
        return roleService.saveRole(operator, mutableRole);
    }

    @PutMapping("/{authority}")
    @Operation(description = "更新角色信息", summary = "更新角色信息")
    public MutableRole update(
            @Parameter(description = "角色名称", required = true) @PathVariable String authority,
            @Parameter(description = "角色信息", required = true) @RequestBody MutableRole mutableRole) {
        Assert.notNull(authority, AuthorizeConstants.ROLE_NAME_NOT_EMPTY);
        mutableRole.setAuthority(authority);
        LoginUser operator = OperatorUtils.getOperator();
        MutableRole result = roleService.updateRole(operator, mutableRole);
        return result;
    }

    @DeleteMapping("/{authority}")
    @Operation(description = "删除角色信息", summary = "删除角色信息")
    public void delete(@Parameter(description = "角色名称", required = true) @PathVariable String authority) {
        MutableRole source = roleService.getRoleByAuthority(authority);
        if (source != null) {
            roleService.deleteRoleById(authority);
        }
    }

    @GetMapping("/auth/{authority}")
    @Operation(description = "查询指定角色的权限信息", summary = "查询指定角色的权限信息")
    public List<AccessPermission> auth(
            @Parameter(description = "角色名称", required = true) @PathVariable String authority,
            @Parameter(description = "模式-0:后台资源,1:APP资源") Integer mode) {
        LoginUser operator = OperatorUtils.getOperator();
        return roleService.getAccessPermissions(operator, authority, mode);
    }

    @PutMapping("/auth/{authority}/{resourceid}")
    @Operation(description = "授予指定角色的特定权限", summary = "授予指定角色的特定权限")
    public void saveAuth(
            @Parameter(description = "角色名称", required = true) @PathVariable String authority,
            @Parameter(description = "资源编号", required = true) @PathVariable String resourceid,
            @Parameter(description = "授权模式.-0:角色,1:用户") Integer mode,
            @Parameter(description = "授权模式.-0:仅使用,1:可管理", schema = @Schema(defaultValue = "1"))
                    @RequestParam(defaultValue = "1")
                    Integer status) {
        LoginUser operator = OperatorUtils.getOperator();
        roleService.saveAuthorization(authority, resourceid, status, operator);
        if (mode == null || !BooleanUtils.toBoolean(mode)) {
            MutableRole role = roleService.getRoleByAuthority(authority);
        } else {
            MutableUser user = userService.getMutableUserByUsername(authority);
        }
    }

    @DeleteMapping("/auth/{authority}/{resourceid}")
    @Operation(description = "删除指定角色的特定权限", summary = "删除指定角色的特定权限")
    public void deleteAuth(
            @Parameter(description = "角色名称", required = true) @PathVariable String authority,
            @Parameter(description = "资源编号", required = true) @PathVariable String resourceid,
            @Parameter(description = "授权模式.-0:角色,1:用户") Integer mode) {
        LoginUser operator = OperatorUtils.getOperator();
        roleService.deleteAuthorization(authority, resourceid, operator);
        if (mode == null || !BooleanUtils.toBoolean(mode)) {
            MutableRole role = roleService.getRoleByAuthority(authority);
        } else {
            MutableUser user = userService.getMutableUserByUsername(authority);
        }
    }

    @PatchMapping("/{authority}/disabled")
    @Operation(description = "禁用角色", summary = "禁用角色")
    public void disabled(
            @Parameter(description = "角色名称", required = true) @PathVariable("authority") String authority) {
        roleService.prohibitRole(0, authority);
        MutableRole role = roleService.getRoleByAuthority(authority);
    }

    @PatchMapping("/{authority}/enabled")
    @Operation(description = "启用角色", summary = "启用角色")
    public void enabled(@Parameter(description = "角色名称", required = true) @PathVariable("authority") String authority) {
        roleService.prohibitRole(1, authority);
        MutableRole role = roleService.getRoleByAuthority(authority);
    }

    @Operation(description = "新增角色分组", summary = "新增角色分组")
    @PostMapping("/group")
    public GroupVo addGroup(@Parameter GroupVo groupVo) {
        GroupVo result = roleService.addGroup(groupVo);
        return result;
    }

    @Operation(description = "修改角色分组", summary = "修改角色分组")
    @PutMapping("/group")
    public GroupVo updateGroup(@Parameter GroupVo groupVo) {
        GroupVo result = roleService.updateGroup(groupVo);
        return result;
    }

    @Operation(description = "删除角色分组", summary = "删除角色分组")
    @DeleteMapping("/group/{groupId}")
    public void deleteGroup(@Parameter(description = "分组ID") @PathVariable String groupId) {
        GroupVo group = roleService.getGroupById(groupId);
        roleService.deleteGroup(groupId);
    }

    @Operation(description = "角色批量分配用户", summary = "角色批量分配用户")
    @PostMapping("/batch/user")
    public void batchAddRoleUser(@Valid @RequestBody BatchAddRoleUser req) {
        MutableRole role = roleService.getRoleByAuthority(req.getRoleId());
        LoginUser user = OperatorUtils.getOperator();
        roleService.batchAddRoleUser(user, req);
    }
}
