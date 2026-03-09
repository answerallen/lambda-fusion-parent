create table la_tenant_datasource
(
    id             varchar(50)       not null comment '绑定ID'
        primary key,
    tenant_id      varchar(32)       not null comment '租户ID',
    datasource_key varchar(50)       null comment '数据源标识',
    schema_status  tinyint default 0 null comment '0未初始化 1已初始化',
    created_at     datetime          null,
    created_by     varchar(50)       null,
    updated_at     datetime          null,
    updated_by     varchar(50)       null
)
    comment '租户数据源绑定表';

