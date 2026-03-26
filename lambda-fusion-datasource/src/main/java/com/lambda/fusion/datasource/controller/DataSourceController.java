package com.lambda.fusion.datasource.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.datasource.model.DataSourceEntity;
import com.lambda.fusion.datasource.model.QueryDataSource;
import com.lambda.fusion.datasource.model.TenantDataSourceEntity;
import com.lambda.fusion.datasource.model.UpsertDataSource;
import com.lambda.fusion.datasource.service.DataSourceManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@SaCheckRole(
        value = {FusionConstants.ROLE_ADMIN, FusionConstants.ROLE_SYSTEM, FusionConstants.ROLE_DEV},
        mode = SaMode.OR)
@Tag(name = "数据源管理")
@RestController
@RequestMapping("/datasource")
public class DataSourceController {

    private final DataSourceManageService dataSourceManageService;

    public DataSourceController(DataSourceManageService dataSourceManageService) {
        this.dataSourceManageService = dataSourceManageService;
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询数据源", description = "分页查询数据源列表")
    public Page<DataSourceEntity> page(@Valid QueryDataSource query) {
        return dataSourceManageService.page(query);
    }

    @GetMapping("/list")
    @Operation(summary = "查询数据源列表", description = "查询数据源列表")
    public List<DataSourceEntity> list() {
        return dataSourceManageService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询数据源详情", description = "按ID查询记录")
    public DataSourceEntity get(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        return dataSourceManageService.getById(id);
    }

    @OperationLog
    @PostMapping
    @Operation(summary = "新增数据源", description = "新增记录")
    public void save(@RequestBody @Valid UpsertDataSource input) {
        dataSourceManageService.save(input);
    }

    @OperationLog
    @PutMapping("/{id}")
    @Operation(summary = "更新数据源", description = "更新记录")
    public void update(
            @Parameter(description = "数据源编号", required = true) @PathVariable String id,
            @RequestBody @Valid UpsertDataSource input) {
        dataSourceManageService.update(id, input);
    }

    @OperationLog
    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据源", description = "删除记录")
    public void delete(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        dataSourceManageService.delete(id);
    }

    @GetMapping("/{id}/test")
    @Operation(summary = "测试数据源连接", description = "测试对应连接")
    public boolean test(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        return dataSourceManageService.test(id);
    }

    @PutMapping("/{id}/enable")
    @Operation(summary = "启用数据源", description = "更新 status=1 并同步到运行时动态数据源")
    public void enable(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        dataSourceManageService.enable(id);
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用数据源", description = "更新 status=0 并从运行时动态数据源移除")
    public void disable(@Parameter(description = "数据源编号", required = true) @PathVariable String id) {
        dataSourceManageService.disable(id);
    }

    @GetMapping("/tenant/status")
    @Operation(summary = "查询租户数据源绑定状态", description = "按租户ID集合查询绑定及初始化状态")
    public List<TenantDataSourceEntity> tenantStatuses(
            @Parameter(description = "租户ID集合", required = true) @RequestParam("tenantIds") List<String> tenantIds) {
        return dataSourceManageService.listTenantDataSources(tenantIds);
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "查询租户数据源绑定", description = "按租户ID查询绑定详情")
    public List<TenantDataSourceEntity> tenantStatus(
            @Parameter(description = "租户ID", required = true) @PathVariable String tenantId) {
        return dataSourceManageService.getTenantDataSources(tenantId);
    }

    @OperationLog
    @PutMapping("/tenant/{tenantId}/bind")
    @Operation(summary = "绑定租户数据源", description = "独立库租户按用途绑定数据源，租户库绑定会重置初始化状态")
    public void bindTenantDatasource(
            @Parameter(description = "租户ID", required = true) @PathVariable String tenantId,
            @Parameter(description = "数据源ID", required = true) @RequestParam("datasourceId") String datasourceId,
            @Parameter(description = "数据库用途", required = true) @RequestParam("usageType") Integer usageType) {
        dataSourceManageService.bindTenantDataSource(
                tenantId, datasourceId, FusionConstants.DatabaseUsageType.fromValue(usageType));
    }

    @OperationLog
    @PostMapping("/tenant/{tenantId}/init")
    @Operation(summary = "初始化租户主库", description = "仅独立库租户可执行，要求先完成数据源绑定")
    public void initTenantDatasource(@Parameter(description = "租户ID", required = true) @PathVariable String tenantId) {
        dataSourceManageService.initTenantDataSource(tenantId);
    }
}
