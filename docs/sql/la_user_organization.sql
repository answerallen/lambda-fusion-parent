create table la_user_organization
(
    USERNAME        varchar(32) not null comment '用户帐号',
    ORGANIZATION_ID varchar(32) not null comment '组织编号',
    TENANT_ID       varchar(32) null comment '租户',
    primary key (USERNAME, ORGANIZATION_ID)
)
    comment '用户组织关系';

INSERT INTO lambda_cloud.la_user_organization (USERNAME, ORGANIZATION_ID, TENANT_ID) VALUES ('111', '2013855273782964226', null);
INSERT INTO lambda_cloud.la_user_organization (USERNAME, ORGANIZATION_ID, TENANT_ID) VALUES ('13303820660', '2015410157403189249', null);
INSERT INTO lambda_cloud.la_user_organization (USERNAME, ORGANIZATION_ID, TENANT_ID) VALUES ('13303820661', 'JsQdTpIA', null);
INSERT INTO lambda_cloud.la_user_organization (USERNAME, ORGANIZATION_ID, TENANT_ID) VALUES ('westboy', '2015410157403189249', null);
