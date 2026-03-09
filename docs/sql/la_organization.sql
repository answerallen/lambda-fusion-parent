create table la_organization
(
    ID           varchar(32)                        not null comment '主键'
        primary key,
    ORG_NAME     varchar(64)                        null,
    ALIAS        varchar(64)                        null,
    ORG_OWNER    varchar(32)                        null comment '组织拥有者',
    ORG_CATEGORY int                                null comment '组织类别',
    ORG_RANK     int                                null comment '组织级别',
    ORG_TYPE     int                                null comment '组织类型',
    PARENT_ID    varchar(32)                        null comment '父ID，NULL 代表顶级',
    PARENT_KEYS  varchar(1000)                      null comment '父节点关键字',
    created_at   datetime default CURRENT_TIMESTAMP not null comment '创建日期',
    REMARKS      varchar(280)                       null comment '备注',
    ENABLED      int      default 1                 not null comment '是否启用，未启用:0,已启用:1',
    TENANT_ID    varchar(32)                        null comment '租户',
    ORDER_NO     int      default 1                 not null comment '组织排序号'
)
    comment '组织机构';

INSERT INTO lambda_cloud.la_organization (ID, ORG_NAME, ALIAS, ORG_OWNER, ORG_CATEGORY, ORG_RANK, ORG_TYPE, PARENT_ID, PARENT_KEYS, created_at, REMARKS, ENABLED, TENANT_ID, ORDER_NO) VALUES ('2015410157403189249', '郑州池续新能源科技有限公司', '池续新能源', null, null, 0, null, '', null, '2026-01-25 21:03:21', '888', 1, null, 999);
INSERT INTO lambda_cloud.la_organization (ID, ORG_NAME, ALIAS, ORG_OWNER, ORG_CATEGORY, ORG_RANK, ORG_TYPE, PARENT_ID, PARENT_KEYS, created_at, REMARKS, ENABLED, TENANT_ID, ORDER_NO) VALUES ('2015792839228628993', '软件部', '软件部', null, null, 1, null, '2015410157403189249', '2015410157403189249', '2026-01-26 22:23:59', null, 1, null, 1);
INSERT INTO lambda_cloud.la_organization (ID, ORG_NAME, ALIAS, ORG_OWNER, ORG_CATEGORY, ORG_RANK, ORG_TYPE, PARENT_ID, PARENT_KEYS, created_at, REMARKS, ENABLED, TENANT_ID, ORDER_NO) VALUES ('2029787736669478914', '拉姆达工作室', '拉姆达', '2013855273782964226', null, 0, null, '', null, '2026-03-06 13:14:43', null, 1, '2013855273782964226', 1);
INSERT INTO lambda_cloud.la_organization (ID, ORG_NAME, ALIAS, ORG_OWNER, ORG_CATEGORY, ORG_RANK, ORG_TYPE, PARENT_ID, PARENT_KEYS, created_at, REMARKS, ENABLED, TENANT_ID, ORDER_NO) VALUES ('JsQdTpIA', '研发中心', '研发中心', null, null, 2, null, 'YgW06azM', 'YgW06azM', '2020-03-20 10:59:05', null, 1, null, 1);
INSERT INTO lambda_cloud.la_organization (ID, ORG_NAME, ALIAS, ORG_OWNER, ORG_CATEGORY, ORG_RANK, ORG_TYPE, PARENT_ID, PARENT_KEYS, created_at, REMARKS, ENABLED, TENANT_ID, ORDER_NO) VALUES ('kdlluie', '总经办', '总经办', null, null, 2, null, 'YgW06azM', 'YgW06azM', '2020-03-20 10:59:05', null, 1, null, 1);
INSERT INTO lambda_cloud.la_organization (ID, ORG_NAME, ALIAS, ORG_OWNER, ORG_CATEGORY, ORG_RANK, ORG_TYPE, PARENT_ID, PARENT_KEYS, created_at, REMARKS, ENABLED, TENANT_ID, ORDER_NO) VALUES ('tlouU3k4', '软件部', '软件部', null, null, 3, null, 'JsQdTpIA', 'YgW06azM-JsQdTpIA', '2020-03-20 11:00:00', null, 1, null, 1);
INSERT INTO lambda_cloud.la_organization (ID, ORG_NAME, ALIAS, ORG_OWNER, ORG_CATEGORY, ORG_RANK, ORG_TYPE, PARENT_ID, PARENT_KEYS, created_at, REMARKS, ENABLED, TENANT_ID, ORDER_NO) VALUES ('YgW06azM', '郑州池续机械设备有限公司', '池续机械', null, null, 1, null, null, null, '2020-03-20 10:42:20', null, 1, null, 4);
