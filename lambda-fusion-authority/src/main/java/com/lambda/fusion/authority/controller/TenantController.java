package com.lambda.fusion.authority.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.authority.model.tenant.TenantEntity;
import com.lambda.fusion.authority.model.tenant.TenantOption;
import com.lambda.fusion.authority.model.tenant.TenantQuery;
import com.lambda.fusion.authority.service.TenantService;
import com.lambda.fusion.core.FusionConstants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 租户信息表相关接口
 */
@SaCheckRole(
        value = {FusionConstants.ROLE_ADMIN, FusionConstants.ROLE_SYSTEM, FusionConstants.ROLE_DEV},
        mode = SaMode.OR)
@Slf4j
@RestController
@RequestMapping("/authority/tenant")
@Tag(name = "租户管理")
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping("/page")
    @Operation(summary = "分页查询所有租户数据列表（V2版本）", description = "使用LambdaQueryWrapper进行分页查询，支持更灵活的排序和查询条件")
    public Page<TenantEntity> pageTenant(@RequestBody TenantQuery queryDTO) {
        return tenantService.pageTenant(queryDTO.getPage(), queryDTO.getLambdaQueryWrapper());
    }

    @GetMapping("/options")
    @Operation(summary = "获取租户下拉列表", description = "查询租户下拉列表")
    public List<TenantOption> tenantOptions() {
        return tenantService.getTenantOptions();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据编号查询租户信息信息", description = "根据id查询租户信息信息")
    public TenantEntity getTenant(@Parameter(description = "租户信息编号", required = true) @PathVariable String id) {
        return tenantService.getById(id);
    }

    @PostMapping
    @Operation(summary = "新增租户信息信息(含LOGO)", description = "新增租户信息信息，支持表单与LOGO同请求提交")
    public TenantEntity saveTenant(
            @Parameter(description = "租户信息信息", required = true) @RequestPart("tenant") String tenant,
            @Parameter(description = "LOGO文件") @RequestPart(value = "logo", required = false) MultipartFile logo,
            @Parameter(description = "OSS客户端名称（可选）") @RequestParam(value = "client", required = false)
                    String clientName) {
        return tenantService.createTenantWithLogo(tenant, logo, clientName);
    }

    @PostMapping(value = "/{id}")
    @Operation(summary = "更新租户信息信息(含LOGO)", description = "更新租户信息信息，支持表单与LOGO同请求提交")
    public TenantEntity updateTenant(
            @Parameter(description = "租户信息编号", required = true) @PathVariable String id,
            @Parameter(description = "租户信息信息", required = true) @RequestPart("tenant") String tenant,
            @Parameter(description = "LOGO文件") @RequestPart(value = "logo", required = false) MultipartFile logo,
            @Parameter(description = "OSS客户端名称（可选）")
                    @RequestParam(value = "client", defaultValue = "default", required = false)
                    String clientName) {
        return tenantService.updateTenantWithLogo(id, tenant, logo, clientName);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除租户信息信息", description = "删除租户信息信息")
    public void deleteTenant(@Parameter(description = "租户编号", required = true) @PathVariable String id) {
        tenantService.deleteTenant(id);
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "启用租户")
    public void enabledTenant(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        tenantService.enableTenant(id);
    }

    @PatchMapping("/{id}/disabled")
    @Operation(summary = "禁用租户")
    public void disabledTenant(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        tenantService.disableTenant(id);
    }
}
