package com.lambda.fusion.datasource.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "数据源租户绑定状态汇总")
public class DataSourceBindingStatus {

    @Schema(description = "数据源ID")
    private String datasourceId;

    @Schema(description = "租户库绑定租户ID")
    private String tenantBindingTenantId;

    @Schema(description = "租户库是否已初始化")
    private Boolean tenantSchemaInitialized;

    @Schema(description = "AI库绑定租户ID")
    private String aiBindingTenantId;

    @Schema(description = "租户库绑定是否存在脏数据冲突")
    private Boolean tenantBindingConflict;

    @Schema(description = "AI库绑定是否存在脏数据冲突")
    private Boolean aiBindingConflict;
}
