package com.lambda.fusion.datasource.model;

import com.lambda.cloud.core.shared.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
//@AutoConverter(target = TenantDataSourceEntity.class)
@Schema(description = "新增/更新动态数据源")
public class UpsertTenantDataSource extends BaseDTO<TenantDataSourceEntity> {

    @NotBlank
    @Schema(description = "数据源编号")
    private String id;

    @NotBlank
    @Schema(description = "数据源名称")
    private String dbName;

    @Schema(description = "数据源描述")
    private String dbDesc;

    @NotBlank
    @Schema(description = "数据源类型")
    private String dbType;

    @Schema(description = "配置参数")
    private String configuration;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "创建人id")
    private String createBy;

    @NotNull
    @Schema(description = "是否启用 0禁用 1启用")
    private Integer enabled;

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "映射数据源id")
    private String mappingOf;

}
