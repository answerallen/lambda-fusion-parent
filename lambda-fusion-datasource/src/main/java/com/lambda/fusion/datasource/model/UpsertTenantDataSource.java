package com.lambda.fusion.datasource.model;

import com.lambda.cloud.core.shared.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "新增/更新租户数据源绑定")
public class UpsertTenantDataSource extends BaseDTO<TenantDataSourceEntity> {

    @NotBlank
    @Schema(description = "绑定ID")
    private String id;

    @NotBlank
    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "数据源标识")
    private String datasourceKey;

    @Schema(description = "Schema 状态")
    private Integer schemaStatus;
}
