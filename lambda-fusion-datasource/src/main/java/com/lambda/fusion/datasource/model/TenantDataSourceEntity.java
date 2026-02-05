package com.lambda.fusion.datasource.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.annotation.FieldMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AutoConverter(target = RemoteDataSource.class)
@TableName("la_tenant_datasource")
@Schema(description = "动态数据源")
public class TenantDataSourceEntity {

    @TableId("ID")
    @Schema(description = "数据源编号")
    private String id;

    @TableField("DB_NAME")
    @Schema(description = "数据源名称")
    @FieldMapping(target = "datasourceName", source = "dbName")
    private String dbName;

    @TableField("DB_DESC")
    @Schema(description = "数据源描述")
    private String dbDesc;

    @TableField("DB_TYPE")
    @Schema(description = "数据源类型")
    private String dbType;

    @TableField("CONFIGURATION")
    @Schema(description = "配置参数")
    private String configuration;

    @TableField("CREATED_AT")
    @Schema(description = "创建时间")
    private Date createdAt;

    @TableField("UPDATED_AT")
    @Schema(description = "更新时间")
    private Date updatedAt;

    @TableField("CREATED_BY")
    @Schema(description = "创建人id")
    private String createdBy;

    @TableField("ENABLED")
    @Schema(description = "是否启用 0禁用 1启用")
    private Integer enabled;

    @TableField("TENANT_ID")
    @Schema(description = "租户id")
    private String tenantId;

    @TableField("MAPPING_OF")
    @Schema(description = "映射数据源id")
    private String mappingOf;
}
