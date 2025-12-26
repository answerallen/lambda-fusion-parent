package com.lambda.fusion.datasource.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;

@TableName("la_datasources")
@Schema(description = "动态数据源")
public class DataSourceEntity {

    @TableId("id")
    @Schema(description = "数据源编号")
    private String id;

    @TableField("datasource_name")
    @Schema(description = "数据源名称")
    private String datasourceName;

    @TableField("driver_class_name")
    @Schema(description = "驱动类名")
    private String driverClassName;

    @TableField("jdbc_url")
    @Schema(description = "连接地址")
    private String jdbcUrl;

    @TableField("username")
    @Schema(description = "用户名")
    private String username;

    @Hidden
    @JsonIgnore
    @TableField("password")
    private String password;

    @TableField("enabled")
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
