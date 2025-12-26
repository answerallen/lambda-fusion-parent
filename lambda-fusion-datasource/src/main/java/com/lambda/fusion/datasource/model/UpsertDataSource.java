package com.lambda.fusion.datasource.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@AutoConverter(target = DataSourceEntity.class)
@Schema(description = "新增/更新动态数据源")
public class UpsertDataSource extends BaseDTO<DataSourceEntity> {

    @NotBlank
    @Schema(description = "数据源编号")
    private String id;

    @NotBlank
    @Schema(description = "数据源名称")
    private String datasourceName;

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDatasourceName() {
        return datasourceName;
    }

    public void setDatasourceName(String datasourceName) {
        this.datasourceName = datasourceName;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
