package com.lambda.fusion.datasource.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lambda.fusion.core.pagination.PageQuery;
import com.lambda.fusion.datasource.DatasourceConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "数据源分页查询")
public class QueryDataSource extends PageQuery<DataSourceEntity> {

    @Schema(description = "数据源名称")
    private String datasourceName;

    @Schema(description = "数据库类型")
    private String dbType;

    @Schema(description = "状态")
    private Integer status;

    public LambdaQueryWrapper<DataSourceEntity> getLambdaQueryWrapper() {
        DatasourceConstants.DatasourceStatus statusEnum = DatasourceConstants.DatasourceStatus.fromCode(status);
        return Wrappers.lambdaQuery(DataSourceEntity.class)
                .like(StringUtils.isNotBlank(datasourceName), DataSourceEntity::getDatasourceName, datasourceName)
                .eq(StringUtils.isNotBlank(dbType), DataSourceEntity::getDbType, dbType)
                .eq(statusEnum != null, DataSourceEntity::getStatus, statusEnum)
                .orderByDesc(DataSourceEntity::getId);
    }
}
