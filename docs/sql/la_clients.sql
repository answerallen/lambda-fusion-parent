create table la_clients
(
    ID         varchar(32)                        not null
        primary key,
    NAME       varchar(255)                       not null comment '客户端名称',
    SECRET     varchar(255)                       not null comment '客户端密钥',
    HOSTS      varchar(255)                       null comment '绑定IP地址',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建日期',
    updated_at datetime default CURRENT_TIMESTAMP not null comment '更新时间',
    EXPIRED    datetime default CURRENT_TIMESTAMP not null comment '过期时间',
    ENABLED    int                                null comment '是否可用',
    REMARKS    varchar(400)                       null,
    TENANT_ID  varchar(32)                        null comment '租户id',
    created_by varchar(64)                        null,
    updated_by varchar(64)                        null
)
    comment '第三方客户端';

INSERT INTO lambda_cloud.la_clients (ID, NAME, SECRET, HOSTS, created_at, updated_at, EXPIRED, ENABLED, REMARKS, TENANT_ID, created_by, updated_by) VALUES ('2014359429889015809', '11111', '4a764cbe-c15c-4a1e-a1c4-29a3f9867c9a', '1.2.2.2', '2026-01-22 23:28:08', '2026-02-14 14:55:47', '2026-01-08 00:00:00', 1, null, null, '13303820660', '13303820660');
INSERT INTO lambda_cloud.la_clients (ID, NAME, SECRET, HOSTS, created_at, updated_at, EXPIRED, ENABLED, REMARKS, TENANT_ID, created_by, updated_by) VALUES ('2030845044124049409', '11111111111', 'a2bfe4e5-d38f-4e49-8f32-da223c5f3e6d', null, '2026-03-09 11:16:04', '2026-03-09 11:16:06', '2026-03-09 11:16:06', 1, null, null, '13303820660', null);
