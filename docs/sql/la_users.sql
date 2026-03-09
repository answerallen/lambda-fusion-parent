create table la_users
(
    USERNAME     varchar(32)                        not null comment '用户帐号'
        primary key,
    PASSWORD     varchar(128)                       not null comment '用户密码',
    NICKNAME     varchar(32)                        not null comment '用户昵称',
    MOBILE       varchar(32)                        null comment '手机号码',
    EMAIL        varchar(280)                       null comment '电子邮箱',
    ENABLED      int                                null comment '是否可用',
    TENANT_ID    varchar(32)                        null comment '租户',
    created_by   varchar(32)                        null,
    created_at   datetime default CURRENT_TIMESTAMP not null comment '创建日期',
    EXPIRED_TIME datetime                           null comment '过期时间'
)
    comment '用户信息表';

INSERT INTO lambda_cloud.la_users (USERNAME, PASSWORD, NICKNAME, MOBILE, EMAIL, ENABLED, TENANT_ID, created_by, created_at, EXPIRED_TIME) VALUES ('111', '8e7b944a244bd31cd89aab092eb8e925c3ce9a3dc3f62d23b9af7422bdabc6f4451b2c91688b5527', '1110000', '11111112', 'westboy@aliyun.com', 1, '2013855273782964226', '13303820660', '2026-03-05 14:59:55', '2027-03-05 14:59:55');
INSERT INTO lambda_cloud.la_users (USERNAME, PASSWORD, NICKNAME, MOBILE, EMAIL, ENABLED, TENANT_ID, created_by, created_at, EXPIRED_TIME) VALUES ('13303820660', 'dd8b4d24b2aff492b1c893894af1f54fe610435d8152678697e055307029e509ebd6ff9dfb1e1e71', '南金金', '13303820660', 'nanjinjin@apache.org', 1, null, null, '2020-01-20 16:07:55', '2099-11-07 00:00:00');
INSERT INTO lambda_cloud.la_users (USERNAME, PASSWORD, NICKNAME, MOBILE, EMAIL, ENABLED, TENANT_ID, created_by, created_at, EXPIRED_TIME) VALUES ('13303820661', '47fd2a6e7dbdae566e136a4fade43326fcf2b012c78f9666ca9505378b73ce6f440750ee49fd3255', 'Jin', '13303820662', 'nanjinjin@lambda.com', 1, null, '13303820660', '2026-01-24 18:48:46', '2027-03-13 00:00:00');
INSERT INTO lambda_cloud.la_users (USERNAME, PASSWORD, NICKNAME, MOBILE, EMAIL, ENABLED, TENANT_ID, created_by, created_at, EXPIRED_TIME) VALUES ('westboy', '16a62fcf2effc5366a7429fbcaf553e740cd2d11a7d28b6bc39582379d09b95cbf7b586e30ff7ac1', '南金金', '18595710660', 'nanjinjin@apache.org', 1, null, '13303820660', '2026-01-27 12:22:50', '2026-07-04 00:00:00');
