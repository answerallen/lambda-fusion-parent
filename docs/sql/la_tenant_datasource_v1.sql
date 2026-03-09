create table la_tenant_datasource_v1
(
    ID            varchar(50)   not null comment '数据源编号'
        primary key,
    DB_NAME       varchar(128)  not null comment '数据源名称',
    DB_DESC       varchar(512)  null comment '数据源描述',
    DB_TYPE       varchar(50)   not null comment '数据源类型：mysql oracle',
    CONFIGURATION text          null comment '配置参数',
    CREATED_AT    datetime      null comment '创建时间',
    UPDATED_AT    datetime      null comment '更新时间',
    CREATE_BY     varchar(50)   null comment '创建人id',
    ENABLED       int default 0 null comment '是否启用  0禁用 1启用',
    TENANT_ID     varchar(32)   null comment '租户id',
    MAPPING_OF    varchar(32)   null comment '映射数据源id'
)
    comment '动态数据源表';

