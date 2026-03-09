create table la_user_password_logs
(
    ID          varchar(32)                        not null comment '日志ID'
        primary key,
    USERNAME    varchar(32)                        not null comment '用户ID',
    PASSWORD    varchar(128)                       not null comment '用户密码',
    UPDATE_TIME datetime default CURRENT_TIMESTAMP not null comment '修改密码的日期'
)
    comment '用户修改密码记录表';

