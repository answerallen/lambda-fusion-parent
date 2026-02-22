package com.lambda.fusion.datasource.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lambda.fusion.core.pagination.Pagination;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "租户数据源分页查询")
public class QueryTenantDataSource extends Pagination<TenantDataSourceEntity> {

    @Schema(description = "数据源名称")
    private String dbName;

    @Schema(description = "租户id")
    private String tenantId;

    public LambdaQueryWrapper<TenantDataSourceEntity> getLambdaQueryWrapper() {
        return Wrappers.lambdaQuery(TenantDataSourceEntity.class)
                .like(StringUtils.isNotBlank(dbName), TenantDataSourceEntity::getDbName, dbName)
                .eq(StringUtils.isNotBlank(tenantId), TenantDataSourceEntity::getTenantId, tenantId)
                .orderByDesc(TenantDataSourceEntity::getId);
    }
}
