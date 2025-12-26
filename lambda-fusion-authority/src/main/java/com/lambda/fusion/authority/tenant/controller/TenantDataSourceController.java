package com.lambda.fusion.authority.tenant.controller;

import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.authority.tenant.model.TenantDataSourceEntity;
import com.lambda.fusion.authority.tenant.model.UpsertTenantDataSource;
import com.lambda.fusion.authority.tenant.service.TenantDataSourceManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "数据源管理")
@RestController
@RequestMapping("/tenant-datasource")
@RequiredArgsConstructor
public class TenantDataSourceController {

    private final TenantDataSourceManageService tenantDataSourceManageService;


    @GetMapping("/list")
    @Operation(summary = "查询数据源列表", description = "查询数据源列表")
    public List<TenantDataSourceEntity> list() {
        return tenantDataSourceManageService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询数据源详情", description = "按ID查询记录")
    public TenantDataSourceEntity get(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        return tenantDataSourceManageService.get(id);
    }

    @OperationLog
    @PostMapping
    @Operation(summary = "新增数据源", description = "新增记录")
    public void save(@RequestBody @Valid UpsertTenantDataSource input) {
        tenantDataSourceManageService.save(input);
    }

    @OperationLog
    @PutMapping("/{id}")
    @Operation(summary = "更新数据源", description = "更新记录")
    public void update(
            @Parameter(description = "数据源编号", required = true) @PathVariable String id,
            @RequestBody @Valid UpsertTenantDataSource input) {
        tenantDataSourceManageService.update(id, input);
    }

    @OperationLog
    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据源", description = "删除记录")
    public void delete(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        tenantDataSourceManageService.delete(id);
    }
}
