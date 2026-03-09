create table la_organization_resources
(
    ORGANIZATION_ID varchar(32) not null comment '机构编号',
    RESOURCE_ID     varchar(32) not null comment '资源编号',
    primary key (ORGANIZATION_ID, RESOURCE_ID)
)
    comment '机构资源关系表';

