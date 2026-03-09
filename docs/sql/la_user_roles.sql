create table la_user_roles
(
    USERNAME  varchar(32) not null comment '用户帐号',
    AUTHORITY varchar(32) not null comment '角色名称',
    TENANT_ID varchar(32) null comment '租户',
    primary key (USERNAME, AUTHORITY)
)
    comment '用户角色关系';

INSERT INTO lambda_cloud.la_user_roles (USERNAME, AUTHORITY, TENANT_ID) VALUES ('111', 'ROLE_TENANT', null);
INSERT INTO lambda_cloud.la_user_roles (USERNAME, AUTHORITY, TENANT_ID) VALUES ('13303820660', 'ROLE_DEV', null);
INSERT INTO lambda_cloud.la_user_roles (USERNAME, AUTHORITY, TENANT_ID) VALUES ('13303820661', 'ROLE_ADMIN', null);
INSERT INTO lambda_cloud.la_user_roles (USERNAME, AUTHORITY, TENANT_ID) VALUES ('13303820661', 'ROLE_MANAGER', null);
INSERT INTO lambda_cloud.la_user_roles (USERNAME, AUTHORITY, TENANT_ID) VALUES ('westboy', 'ROLE_ADMIN', null);
