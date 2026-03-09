create table la_role_groups
(
    GROUP_ID   varchar(32) not null comment '主键'
        primary key,
    GROUP_NAME varchar(64) not null comment '分组名称',
    TENANT_ID  varchar(32) null comment '租户ID'
)
    comment '角色分组';

INSERT INTO lambda_cloud.la_role_groups (GROUP_ID, GROUP_NAME, TENANT_ID) VALUES ('2016824170421256194', '运营商组', null);
INSERT INTO lambda_cloud.la_role_groups (GROUP_ID, GROUP_NAME, TENANT_ID) VALUES ('default', '默认分组', null);
