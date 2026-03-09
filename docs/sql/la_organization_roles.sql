create table la_organization_roles
(
    AUTHORITY       varchar(32) not null comment '角色名称',
    ORGANIZATION_ID varchar(32) not null comment '机构编号',
    primary key (AUTHORITY, ORGANIZATION_ID)
)
    comment '组织角色关系表';

