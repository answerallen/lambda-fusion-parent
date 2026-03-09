create table la_roles
(
    AUTHORITY  varchar(32)                        not null comment '角色名称'
        primary key,
    ALIAS      varchar(128)                       not null comment '角色别名',
    ICON       varchar(32)                        null comment '用户图标',
    REMARKS    varchar(280)                       null comment '角色描述',
    TENANT_ID  varchar(32)                        null comment '租户',
    HIDDEN     int      default 0                 not null comment '是否隐藏',
    ENABLED    int      default 1                 not null comment '是否启用，未启用:0,已启用:1',
    DATA_TYPE  int      default 0                 not null comment '数据层级与数据权限视图层级相同',
    ROLE_TYPE  int      default 0                 not null comment '角色类型,功能角色:0,数据角色:1',
    GROUP_ID   varchar(32)                        null comment '分组ID',
    created_by varchar(32)                        null comment 'created_by',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建日期',
    OWNER      varchar(32)                        null
)
    comment '角色信息表';

INSERT INTO lambda_cloud.la_roles (AUTHORITY, ALIAS, ICON, REMARKS, TENANT_ID, HIDDEN, ENABLED, DATA_TYPE, ROLE_TYPE, GROUP_ID, created_by, created_at, OWNER) VALUES ('afeacc445fc54b77bc8400d84364bbdb', '11111111111111', null, '1111111', '2013855273782964226', 0, 1, 0, 0, 'default', '111', '2026-03-07 21:58:09', '2013855273782964226');
INSERT INTO lambda_cloud.la_roles (AUTHORITY, ALIAS, ICON, REMARKS, TENANT_ID, HIDDEN, ENABLED, DATA_TYPE, ROLE_TYPE, GROUP_ID, created_by, created_at, OWNER) VALUES ('ROLE_ADMIN', '后台管理员', null, '系统内置角色，不允许删除', null, 0, 1, 0, 0, 'default', null, '2017-06-28 09:27:31', null);
INSERT INTO lambda_cloud.la_roles (AUTHORITY, ALIAS, ICON, REMARKS, TENANT_ID, HIDDEN, ENABLED, DATA_TYPE, ROLE_TYPE, GROUP_ID, created_by, created_at, OWNER) VALUES ('ROLE_DEV', '开发工程师', null, '系统内置角色，不允许删除', null, 0, 1, 0, 0, 'default', null, '2017-06-28 09:22:50', null);
INSERT INTO lambda_cloud.la_roles (AUTHORITY, ALIAS, ICON, REMARKS, TENANT_ID, HIDDEN, ENABLED, DATA_TYPE, ROLE_TYPE, GROUP_ID, created_by, created_at, OWNER) VALUES ('ROLE_MANAGER', '部门主管', null, '系统内置角色，不允许删除', null, 0, 1, 0, 0, 'default', null, '2020-04-20 14:22:40', null);
INSERT INTO lambda_cloud.la_roles (AUTHORITY, ALIAS, ICON, REMARKS, TENANT_ID, HIDDEN, ENABLED, DATA_TYPE, ROLE_TYPE, GROUP_ID, created_by, created_at, OWNER) VALUES ('ROLE_ORG', '组织机构管理员', null, '系统内置角色，不允许删除', null, 0, 1, 0, 0, 'default', null, '2021-07-01 11:51:31', null);
INSERT INTO lambda_cloud.la_roles (AUTHORITY, ALIAS, ICON, REMARKS, TENANT_ID, HIDDEN, ENABLED, DATA_TYPE, ROLE_TYPE, GROUP_ID, created_by, created_at, OWNER) VALUES ('ROLE_SYSTEM', '系统管理员', null, '系统内置角色，不允许删除', null, 0, 1, 0, 0, 'default', null, '2019-03-12 18:08:03', null);
INSERT INTO lambda_cloud.la_roles (AUTHORITY, ALIAS, ICON, REMARKS, TENANT_ID, HIDDEN, ENABLED, DATA_TYPE, ROLE_TYPE, GROUP_ID, created_by, created_at, OWNER) VALUES ('ROLE_TENANT', '租户管理员', null, '系统内置角色，不允许删除', null, 0, 1, 0, 0, 'default', null, '2022-04-21 09:27:31', null);
INSERT INTO lambda_cloud.la_roles (AUTHORITY, ALIAS, ICON, REMARKS, TENANT_ID, HIDDEN, ENABLED, DATA_TYPE, ROLE_TYPE, GROUP_ID, created_by, created_at, OWNER) VALUES ('ROLE_USER', '普通用户', null, '系统内置角色，不允许删除', null, 0, 1, 0, 0, 'default', null, '2017-06-28 09:27:31', null);
