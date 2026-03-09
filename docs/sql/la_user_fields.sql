create table la_user_fields
(
    USERNAME    varchar(30)  not null comment '员工编号',
    FIELD_NAME  varchar(30)  not null comment '字段名称',
    FIELD_VALUE varchar(200) null comment '字段值',
    TENANT_ID   varchar(32)  null comment '租户',
    primary key (USERNAME, FIELD_NAME)
)
    comment '用户扩展字段信息表';

