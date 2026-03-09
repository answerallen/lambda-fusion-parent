create table la_tenant
(
    TENANT_ID             varchar(32)                        null comment '租户ID',
    TENANT_CODE           varchar(32)                        not null comment '租户编码'
        primary key,
    TENANT_NAME           varchar(100)                       not null comment '租户名称',
    TENANT_ADDRESS        varchar(500)                       null comment '租户地址',
    TENANT_WEBSITE        varchar(255)                       null comment '网站地址',
    TENANT_DESC           varchar(500)                       null comment '租户描述',
    TENANT_LOGO           varchar(255)                       null comment 'LOGO地址',
    LIAISON_MAN           varchar(20)                        null comment '联系人',
    LIAISON_PHONE         varchar(20)                        not null comment '联系电话',
    enterprise_code       varchar(20)                        not null comment '企业代码',
    enterprise_name       varchar(32)                        null,
    ICP_PUT_ON_RECORD     varchar(50)                        null comment 'ICP备案号',
    NETWORK_PUT_ON_RECORD varchar(50)                        null comment '联网备案号',
    status                int      default 1                 not null comment '是否启用，未启用:0,已启用:1',
    EXAMINE_STATE         int      default 0                 null comment '审核状态，未审核:0,审核通过:1',
    OWNER                 varchar(32)                        null comment '拥有者',
    ALIAS                 varchar(64)                        null comment '别名',
    isolation_mode        int                                null comment '隔离类型',
    AREA_CODE             varchar(64)                        null comment '区域编码',
    CONFIG                longtext                           null comment '租户配置',
    created_by            varchar(20)                        null comment '创建人',
    created_at            datetime default CURRENT_TIMESTAMP null comment '创建时间',
    UPDATED_BY            varchar(20)                        null comment '最后修改人',
    UPDATED_AT            datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '租户信息';

INSERT INTO lambda_cloud.la_tenant (TENANT_ID, TENANT_CODE, TENANT_NAME, TENANT_ADDRESS, TENANT_WEBSITE, TENANT_DESC, TENANT_LOGO, LIAISON_MAN, LIAISON_PHONE, enterprise_code, enterprise_name, ICP_PUT_ON_RECORD, NETWORK_PUT_ON_RECORD, status, EXAMINE_STATE, OWNER, ALIAS, isolation_mode, AREA_CODE, CONFIG, created_by, created_at, UPDATED_BY, UPDATED_AT) VALUES ('2029465586766987265', '100000001', '郑州池续新能源', '河南省郑州市中原区', null, null, null, '张鑫', '13303820660', '1000000000', '郑州池续新能源科技有限公司', null, null, 0, 0, null, null, 1, null, null, '13303820660', '2026-03-05 15:54:36', '13303820660', '2026-03-05 15:57:49');
INSERT INTO lambda_cloud.la_tenant (TENANT_ID, TENANT_CODE, TENANT_NAME, TENANT_ADDRESS, TENANT_WEBSITE, TENANT_DESC, TENANT_LOGO, LIAISON_MAN, LIAISON_PHONE, enterprise_code, enterprise_name, ICP_PUT_ON_RECORD, NETWORK_PUT_ON_RECORD, status, EXAMINE_STATE, OWNER, ALIAS, isolation_mode, AREA_CODE, CONFIG, created_by, created_at, UPDATED_BY, UPDATED_AT) VALUES ('2013855273782964226', '100000002', '郑州市移动公司', '河南省郑州', '111', '1111111', 'http://cdn.devcms.cn/tenant/logo/2027732040739885058.gif', '张鑫', '13303820660', '100001', '南金金', '1111111', '11111111111111111', 1, 1, null, '111', 1, null, null, null, '2026-01-21 14:04:53', '13303820660', '2026-03-05 17:19:56');
