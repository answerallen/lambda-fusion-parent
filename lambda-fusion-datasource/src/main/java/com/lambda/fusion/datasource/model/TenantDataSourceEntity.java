package com.lambda.fusion.datasource.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * create table la_tenant_datasource
 * (
 * id                 varchar(50)  not null primary key comment '绑定ID',
 * tenant_id          varchar(32)  not null comment '租户ID',
 * datasource_key     varchar(50)  null comment '数据源ID',
 * schema_status      tinyint      default 0 comment '0未初始化 1已初始化',
 * created_at         datetime,
 * created_by         varchar(50),
 * updated_at         datetime,
 * updated_by         varchar(50)
 * )
 * comment '租户数据源绑定表';
 */
@Setter
@Getter
@TableName("la_tenant_datasource")
@Schema(description = "动态数据源")
public class TenantDataSourceEntity extends BaseEntity {

    public static final int SCHEMA_UNINITIALIZED = 0;
    public static final int SCHEMA_INITIALIZED = 1;

    @TableId("id")
    @Schema(description = "绑定ID")
    private String id;

    @TableField("tenant_id")
    @Schema(description = "租户ID")
    private String tenantId;

    @TableField("datasource_key")
    @Schema(description = "数据源ID")
    private String datasourceId;

    @TableField("usage_type")
    @Schema(description = "数据库用途")
    private FusionConstants.DatabaseUsageType usageType;

    @TableField("schema_status")
    @Schema(description = "Schema 状态")
    private Integer schemaStatus;

    public boolean isSchemaInitialized() {
        return Integer.valueOf(SCHEMA_INITIALIZED).equals(schemaStatus);
    }
}
