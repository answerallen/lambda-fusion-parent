create table la_operation_logs
(
    ID          varchar(20)                        not null
        primary key,
    METHOD      varchar(255)                       null comment '方法',
    DESCRIPTION varchar(255)                       null comment '描述',
    OPERATOR    varchar(255)                       null comment '操作人员',
    OPERATION   varchar(255)                       null comment '操作方式',
    CREATE_DATE datetime default CURRENT_TIMESTAMP not null comment '操作时间',
    COST        int                                null comment '处理耗时',
    DETAIL      varchar(4000)                      null comment '详情',
    TENANT_ID   varchar(32)                        null comment '租户',
    MODULE      varchar(32)                        null comment '模块名',
    OPERATOR_ID varchar(32)                        null comment '用户ID'
)
    comment '操作日志';

