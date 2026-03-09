create table la_datasources
(
    id              varchar(32)                   not null comment '资源ID'
        primary key,
    datasource_key  varchar(128)                  not null comment '逻辑资源标识（系统用）',
    datasource_name varchar(128)                  not null comment '数据源名称（展示用）',
    db_type         varchar(32)                   not null comment 'mysql pg oracle',
    usage_type      varchar(32)                   not null comment '用途：BUSINESS SYSTEM TENANT',
    host            varchar(256)                  not null,
    port            int                           not null,
    db_name         varchar(128)                  not null,
    username        varchar(128)                  null,
    password        varchar(512)                  null,
    node_role       varchar(32) default 'PRIMARY' null comment 'PRIMARY 主库, REPLICA 从库',
    status          tinyint     default 1         null comment '0下线 1在线',
    tags            json                          null comment '资源标签（如 {"env":"prod","region":"sg","cost":"premium"}）',
    extra_config    json                          null comment '扩展配置',
    created_by      varchar(32)                   null,
    created_at      datetime                      null,
    updated_by      varchar(32)                   null,
    updated_at      datetime                      null
)
    comment '数据库资源池';

