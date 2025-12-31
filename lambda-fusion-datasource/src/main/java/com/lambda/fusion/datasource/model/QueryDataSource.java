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
@Schema(description = "数据源分页查询")
public class QueryDataSource extends Pagination<DataSourceEntity> {

    @Schema(description = "数据源名称")
    private String datasourceName;

    public LambdaQueryWrapper<DataSourceEntity> getLambdaQueryWrapper() {
        return Wrappers.lambdaQuery(DataSourceEntity.class)
                .like(StringUtils.isNotBlank(datasourceName), DataSourceEntity::getDatasourceName, datasourceName)
                .orderByDesc(DataSourceEntity::getId);
    }
}
