create table la_datasources_v1
(
    id                varchar(32)   not null comment '数据源编号'
        primary key,
    datasource_name   varchar(128)  not null comment '数据源名称',
    driver_class_name varchar(512)  null comment '驱动类名',
    jdbc_url          varchar(1024) not null comment '连接地址',
    username          varchar(512)  null comment '用户名',
    password          varchar(512)  null comment '密码',
    enabled           int default 0 null comment '是否启用  0禁用 1启用',
    created_by        varchar(32)   null,
    created_at        datetime      null,
    updated_by        varchar(32)   null,
    updated_at        datetime      null
)
    comment '动态数据源表';

INSERT INTO lambda_cloud.la_datasources_v1 (id, datasource_name, driver_class_name, jdbc_url, username, password, enabled, created_by, created_at, updated_by, updated_at) VALUES ('111', '111', '222', '333', '444', '55555', 0, null, null, null, null);
