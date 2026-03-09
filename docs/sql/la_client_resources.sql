create table la_client_resources
(
    CLIENT_ID   char(19)    not null,
    RESOURCE_ID varchar(32) not null comment '资源编号',
    primary key (CLIENT_ID, RESOURCE_ID)
)
    comment '客户端资源关系表';

