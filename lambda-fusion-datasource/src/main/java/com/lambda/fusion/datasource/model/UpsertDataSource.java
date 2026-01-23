package com.lambda.fusion.datasource.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AutoConverter(target = DataSourceEntity.class)
@Schema(description = "新增/更新动态数据源")
public class UpsertDataSource extends BaseDTO<DataSourceEntity> {

    @Schema(description = "数据源编号")
    private String id;

    @NotBlank
    @Schema(description = "数据源名称")
    private String datasourceName;

    @NotBlank
    @Schema(description = "驱动类名")
    private String driverClassName;

    @NotBlank
    @Schema(description = "连接地址")
    private String jdbcUrl;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @NotNull
    @Schema(description = "是否启用 0禁用 1启用")
    private Integer enabled;
}
