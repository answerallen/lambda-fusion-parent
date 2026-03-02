package com.lambda.fusion.datasource.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.datasource.DatasourceConstants;
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
    @Schema(description = "逻辑资源标识")
    private String datasourceKey;

    @NotBlank
    @Schema(description = "数据源名称")
    private String datasourceName;

    @NotBlank
    @Schema(description = "数据库类型")
    private String dbType;

    @NotBlank
    @Schema(description = "用途")
    private String usageType;

    @NotBlank
    @Schema(description = "主机地址")
    private String host;

    @NotNull
    @Schema(description = "端口")
    private Integer port;

    @NotBlank
    @Schema(description = "数据库名称")
    private String dbName;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "节点角色")
    private String nodeRole;

    @Schema(description = "状态")
    private DatasourceConstants.DatasourceStatus status;

    @Schema(description = "资源标签")
    private String tags;

    @Schema(description = "扩展配置")
    private String extraConfig;
}
