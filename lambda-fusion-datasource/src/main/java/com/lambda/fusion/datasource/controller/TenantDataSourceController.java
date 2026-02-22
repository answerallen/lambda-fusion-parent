package com.lambda.fusion.datasource.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertTenantDataSource;
import com.lambda.fusion.datasource.service.TenantDataSourceManageService;
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

    @GetMapping("/page")
    @Operation(summary = "分页查询数据源", description = "分页查询数据源列表")
    public IPage<TenantDataSourceEntity> page(@Valid com.lambda.fusion.datasource.model.QueryTenantDataSource query) {
        return tenantDataSourceManageService.page(query);
    }

    @GetMapping("/list")
    @Operation(summary = "查询数据源列表", description = "查询数据源列表")
    public List<TenantDataSourceEntity> list() {
        return tenantDataSourceManageService.listAll();
    }

    @GetMapping("/{id}/test")
    @Operation(summary = "测试数据源连接", description = "测试对应连接")
    public boolean test(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        return tenantDataSourceManageService.test(id);
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用数据源", description = "更新 enabled=1 并同步到运行时动态数据源")
    public void enable(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        tenantDataSourceManageService.enable(id);
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用数据源", description = "更新 enabled=0 并从运行时动态数据源移除")
    public void disable(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        tenantDataSourceManageService.disable(id);
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
