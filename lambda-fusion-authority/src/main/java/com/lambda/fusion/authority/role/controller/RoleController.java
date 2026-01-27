package com.lambda.fusion.authority.role.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.authority.role.model.AccessPermission;
import com.lambda.fusion.authority.role.model.BatchRoleUserAssignmentRequest;
import com.lambda.fusion.authority.role.model.CreateRole;
import com.lambda.fusion.authority.role.model.Group;
import com.lambda.fusion.authority.role.model.GroupRole;
import com.lambda.fusion.authority.role.model.Role;
import com.lambda.fusion.authority.role.model.UpdateRole;
import com.lambda.fusion.authority.role.service.InternalRoleService;
import com.lambda.fusion.authority.role.service.RoleService;
import com.lambda.fusion.authority.tenant.manager.TenantAuthorizeManager;
import com.lambda.fusion.authority.user.service.UserService;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserPrincipal;
import com.lambda.fusion.core.utils.LoginUserUtils;
import com.lambda.fusion.core.utils.SqlParamUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户角色API
 */
@RestController
@RequestMapping({"/authority/roles", "/authority/roles"})
@Tag(name = "角色管理")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class RoleController {

    private final RoleService roleService;
    private final UserService userService;
    private final InternalRoleService internalRoleService;

    private TenantAuthorizeManager tenantResourceManager;

    @Autowired(required = false)
    public void setTenantResourceManager(TenantAuthorizeManager tenantResourceManager) {
        this.tenantResourceManager = tenantResourceManager;
    }

    @GetMapping
    @SaCheckLogin
    @Operation(description = "获取所有角色列表", summary = "获取所有角色列表")
    public List<Role> list() {
        UserPrincipal userPrincipal = LoginUserUtils.getLoginUser();
        return roleService.getAllRoles(userPrincipal);
    }

    @GetMapping("/grouped")
    @Operation(
            description = "获取所有角色分组列表",
            summary = "获取所有角色分组列表",
            parameters = {@Parameter(name = "tenant_id", description = "租户id")})
    public List<GroupRole> grouped() {
        UserPrincipal userPrincipal = LoginUserUtils.getLoginUser();
        return roleService.grouped(userPrincipal, userPrincipal.getTenantId());
    }

    @GetMapping({"/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(description = "分页查询所有角色列表", summary = "分页查询所有角色列表")
    public Page<Role> page(
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            String alias,
            String groupId) {
        UserPrincipal userPrincipal = LoginUserUtils.getLoginUser();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(4);
        if (ObjectUtil.isNotNull(groupId)) {
            parameters.put("groupId", groupId);
        }
        if (StringUtils.isNotBlank(alias)) {
            parameters.put("alias", SqlParamUtils.fuzzyQuery(alias));
        }
        Set<String> excludes = Sets.newHashSet();
        excludes.add(FusionConstants.ROLE_USER);
        excludes.add(FusionConstants.ROLE_HMAC);
        excludes.add(FusionConstants.ROLE_DEV);
        excludes.add(FusionConstants.ROLE_SYSTEM);
        excludes.add(FusionConstants.ROLE_TENANT);
        if (!userPrincipal.isDev()) {
            excludes.add(FusionConstants.ROLE_ADMIN);
        }
        Set<String> queryExclude = internalRoleService.queryExclude(userPrincipal);
        excludes.addAll(queryExclude);
        parameters.put("excludes", excludes);
        String tenantId = userPrincipal.getTenantId();
        if (StringUtils.isNotBlank(tenantId)) {
            parameters.put("tenant_id", tenantId);
        }
        return roleService.getAllRoles(new Page<>(number, size), parameters);
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
    public Role update(@Parameter(description = "角色名称", required = true) @PathVariable String authority) {
        Assert.notNull(authority, "角色名称不能为空！");
        return roleService.getRoleByAuthority(authority);
    }

    @PostMapping
    @Operation(description = "新增角色信息", summary = "新增角色信息")
    public Role add(@Parameter(description = "角色信息", required = true) @RequestBody CreateRole createRole) {
        UserPrincipal userPrincipal = LoginUserUtils.getLoginUser();
        return roleService.saveRole(userPrincipal, createRole);
    }

    @PutMapping("/{authority}")
    @Operation(description = "更新角色信息", summary = "更新角色信息")
    public Role update(
            @Parameter(description = "角色名称", required = true) @PathVariable String authority,
            @Parameter(description = "角色信息", required = true) @RequestBody UpdateRole updateRole) {
        Assert.notNull(authority, "角色名称不能为空！");
        updateRole.setAuthority(authority);
        UserPrincipal userPrincipal = LoginUserUtils.getLoginUser();
        return roleService.updateRole(userPrincipal, updateRole);
    }

    @DeleteMapping("/{authority}")
    @Operation(description = "删除角色信息", summary = "删除角色信息")
    public void delete(@Parameter(description = "角色名称", required = true) @PathVariable String authority) {
        Role source = roleService.getRoleByAuthority(authority);
        if (source != null) {
            roleService.deleteRoleById(authority);
        }
    }

    @GetMapping("/{authority}/permissions")
    @Operation(description = "查询指定角色的权限信息", summary = "查询指定角色的权限信息")
    public List<AccessPermission> auth(
            @Parameter(description = "角色名称", required = true) @PathVariable String authority,
            @Parameter(description = "模式-0:后台资源,1:APP资源") Integer mode) {
        UserPrincipal userPrincipal = LoginUserUtils.getLoginUser();
        return roleService.getAccessPermissions(userPrincipal, authority, mode);
    }

    @PatchMapping("/{authority}/disabled")
    @Operation(description = "禁用角色", summary = "禁用角色")
    public void disabled(@PathVariable @Parameter(description = "角色名称", required = true) String authority) {
        roleService.prohibitRole(0, authority);
    }

    @PatchMapping("/{authority}/enabled")
    @Operation(description = "启用角色", summary = "启用角色")
    public void enabled(@PathVariable @Parameter(description = "角色名称", required = true) String authority) {
        roleService.prohibitRole(1, authority);
    }

    @PutMapping("/{authority}/grant/{resourceId}")
    @Operation(description = "授予指定角色的特定权限", summary = "授予指定角色的特定权限")
    public void grantRolePermission(
            @Parameter(description = "角色名称", required = true) @PathVariable String authority,
            @Parameter(description = "资源编号", required = true) @PathVariable String resourceId,
            @Parameter(description = "授权模式.-0:仅使用,1:可管理", schema = @Schema(defaultValue = "1"))
                    @RequestParam(defaultValue = "1")
                    Integer status) {
        roleService.grantRolePermission(authority, resourceId, status, LoginUserUtils.getLoginUser());
    }

    @DeleteMapping("/{authority}/grant/{resourceId}")
    @Operation(description = "删除指定角色的特定权限", summary = "删除指定角色的特定权限")
    public void revokeRolePermission(
            @Parameter(description = "角色名称", required = true) @PathVariable String authority,
            @Parameter(description = "资源编号", required = true) @PathVariable String resourceId) {
        roleService.revokeRolePermission(authority, resourceId, LoginUserUtils.getLoginUser());
    }

    @Operation(description = "角色批量分配用户", summary = "角色批量分配用户")
    @PostMapping("/assignUsers")
    public void assignUsersToRole(@Valid @RequestBody BatchRoleUserAssignmentRequest req) {
        UserPrincipal userPrincipal = LoginUserUtils.getLoginUser();
        roleService.assignUsersToRole(userPrincipal, req);
    }

    @Operation(description = "分组列表", summary = "分组列表")
    @GetMapping("/group")
    public List<Group> listGroups() {
        UserPrincipal userPrincipal = LoginUserUtils.getLoginUser();
        return roleService.listGroups(userPrincipal);
    }

    @Operation(description = "新增角色分组", summary = "新增角色分组")
    @PostMapping("/group")
    public Group addGroup(@Parameter Group group) {
        return roleService.addGroup(group);
    }

    @Operation(description = "修改角色分组", summary = "修改角色分组")
    @PutMapping("/group")
    public Group updateGroup(@Parameter Group group) {
        return roleService.updateGroup(group);
    }

    @Operation(description = "删除角色分组", summary = "删除角色分组")
    @DeleteMapping("/group/{groupId}")
    public void deleteGroup(@Parameter(description = "分组ID") @PathVariable String groupId) {
        roleService.deleteGroup(groupId);
    }
}
