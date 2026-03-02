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

    @Schema(description = "数据源标识")
    private String datasourceKey;

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "Schema 状态")
    private Integer schemaStatus;

    public LambdaQueryWrapper<TenantDataSourceEntity> getLambdaQueryWrapper() {
        return Wrappers.lambdaQuery(TenantDataSourceEntity.class)
                .like(StringUtils.isNotBlank(datasourceKey), TenantDataSourceEntity::getDatasourceKey, datasourceKey)
                .eq(StringUtils.isNotBlank(tenantId), TenantDataSourceEntity::getTenantId, tenantId)
                .eq(schemaStatus != null, TenantDataSourceEntity::getSchemaStatus, schemaStatus)
                .orderByDesc(TenantDataSourceEntity::getId);
    }
}
