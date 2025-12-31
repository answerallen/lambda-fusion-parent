package com.lambda.fusion.datasource.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lambda.cloud.core.annotation.AutoConverter;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AutoConverter(target = RemoteDataSource.class)
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
}
