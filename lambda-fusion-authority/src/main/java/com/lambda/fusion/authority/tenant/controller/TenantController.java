package com.lambda.fusion.authority.tenant.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.tenant.model.Tenant;
import com.lambda.fusion.authority.tenant.model.TenantEntity;
import com.lambda.fusion.authority.tenant.model.TenantOption;
import com.lambda.fusion.authority.tenant.model.TenantQuery;
import com.lambda.fusion.authority.tenant.service.TenantService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 租户信息表相关接口
 */
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
    public Page<TenantEntity> pageV2(@RequestBody TenantQuery queryDTO) {
        return tenantService.page(queryDTO.getPage(), queryDTO.getLambdaQueryWrapper());
    }

    @GetMapping("/options")
    @Operation(summary = "获取租户下拉列表", description = "查询租户下拉列表")
    public List<TenantOption> tenantOptions() {
        return tenantService.getTenantOptions();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据编号查询租户信息信息", description = "根据id查询租户信息信息")
    public TenantEntity get(@Parameter(description = "租户信息编号", required = true) @PathVariable String id) {
        return tenantService.getById(id);
    }

    @PostMapping
    @Operation(summary = "新增租户信息信息", description = "新增租户信息信息")
    public TenantEntity save(@Parameter(description = "租户信息信息", required = true) @RequestBody Tenant entity) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = new TenantEntity();
        BeanUtils.copyProperties(entity, target);
        String id = IdWorker.getIdStr();
        String tenantId = operator.getTenantId();
        target.setOwner(tenantId);
        target.setTenantId(id);
        tenantService.save(target);
        return tenantService.getById(target.getTenantId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新租户信息信息", description = "更新租户信息信息")
    public TenantEntity update(
            @Parameter(description = "租户信息编号", required = true) @PathVariable String id,
            @Parameter(description = "租户信息信息", required = true) @RequestBody Tenant tenant) {
        LoginUser operator = OperatorUtils.getOperator();
        TenantEntity target = new TenantEntity();
        BeanUtils.copyProperties(tenant, target);
        target.setTenantId(id);
        target.setUpdatedBy(operator.getName());
        tenantService.updateById(target);
        return tenantService.getById(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除租户信息信息", description = "删除租户信息信息")
    public void delete(@Parameter(description = "租户编号", required = true) @PathVariable String id) {
        LoginUser operator = OperatorUtils.getOperator();
        tenantService.deleteTenant(operator, id);
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "启用租户")
    public void enabled(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("租户编号不能为空");
        }
        TenantEntity tenant = tenantService.getById(id);
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(id);
        }
        tenantService.prohibitTenant(operator, 1, id);
    }

    @PatchMapping("/{id}/disabled")
    @Operation(summary = "禁用租户")
    public void disabled(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("租户编号不能为空");
        }
        TenantEntity tenant = tenantService.getById(id);
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(id);
        }
        tenantService.prohibitTenant(operator, 0, id);
    }

    @PatchMapping("/{id}/stop")
    @Operation(summary = "停用租户")
    public void stop(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("租户编号不能为空");
        }
        TenantEntity tenant = tenantService.getById(id);
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(id);
        }
        tenantService.prohibitTenant(operator, -1, id);
    }

    @PatchMapping("/{id}/examine")
    @Operation(summary = "审核租户")
    public void examine(@PathVariable @Parameter(description = "租户编号", required = true) String id) {
        LoginUser operator = OperatorUtils.getOperator();
        if (id == null) {
            throw AuthorityBusinessException.invalidParameter("租户编号不能为空");
        }
        TenantEntity tenant = tenantService.getById(id);
        if (tenant == null) {
            throw AuthorityBusinessException.tenantNotFound(id);
        }
        tenantService.examineTenant(operator, 1, id);
    }
}
