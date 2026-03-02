package com.lambda.fusion.datasource.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.cloud.core.annotation.FieldMapping;
import com.lambda.fusion.core.FusionConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 create table la_tenant_datasource
 (
 id                 varchar(50)  not null primary key comment '绑定ID',

 tenant_id          varchar(32)  not null comment '租户ID',

 access_mode        varchar(16)  default 'RW'
 comment '访问模式：RW RO',

 mapping_strategy   varchar(20)  default 'DIRECT'
 comment 'DIRECT CLONE SHARED',

 source_binding_id  varchar(50)  null
 comment '来源绑定ID',

 binding_status     tinyint      default 1
 comment '0禁用 1启用',

 created_at         datetime,
 updated_at         datetime,
 created_by         varchar(50)
 )
 comment '租户数据源绑定表';
 */
@Setter
@Getter
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
    private FusionConstants.ActiveStatus enabled;

    @TableField("TENANT_ID")
    @Schema(description = "租户id")
    private String tenantId;

    @TableField("MAPPING_OF")
    @Schema(description = "映射数据源id")
    private String mappingOf;
}
