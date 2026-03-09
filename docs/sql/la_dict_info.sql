create table la_dict_info
(
    id           varchar(32)          not null comment '主键'
        primary key,
    REMARKS      varchar(100)         null comment '备注',
    parent_keys  varchar(64)          null,
    sort_no      varchar(20)          not null comment '排序编码',
    created_at   datetime             null comment '创建时间',
    updated_at   datetime             null comment '更新时间',
    created_by   varchar(32)          null comment '创建人ID',
    updated_by   varchar(32)          null comment '修改人ID',
    del_flag     tinyint(1) default 0 not null comment '删除状态（0 正常 1删除）',
    enable_state int        default 1 not null comment '状态 0禁用 1启用',
    dict_type    varchar(64)          null,
    field_type   varchar(64)          null,
    field_name   varchar(64)          null,
    select_able  int                  null,
    level        int                  null,
    parent_id    varchar(128)         null,
    tenant_id    varchar(64)          null,
    extra        int                  null
)
    comment '字典表';

INSERT INTO lambda_cloud.la_dict_info (id, REMARKS, parent_keys, sort_no, created_at, updated_at, created_by, updated_by, del_flag, enable_state, dict_type, field_type, field_name, select_able, level, parent_id, tenant_id, extra) VALUES ('2016333454972604417', null, null, '0', '2026-01-28 10:12:12', '2026-03-01 21:37:58', '13303820661', '13303820660', 0, 1, 'SYS_ROLE_TYPE', '11111111111111111', '1111111', 0, 1, null, null, null);
INSERT INTO lambda_cloud.la_dict_info (id, REMARKS, parent_keys, sort_no, created_at, updated_at, created_by, updated_by, del_flag, enable_state, dict_type, field_type, field_name, select_able, level, parent_id, tenant_id, extra) VALUES ('2016822669904805890', null, null, '0', '2026-01-29 18:36:10', '2026-03-01 22:00:31', '13303820660', '13303820660', 0, 1, 'SEX', '1', '男', 1, 1, null, null, null);
INSERT INTO lambda_cloud.la_dict_info (id, REMARKS, parent_keys, sort_no, created_at, updated_at, created_by, updated_by, del_flag, enable_state, dict_type, field_type, field_name, select_able, level, parent_id, tenant_id, extra) VALUES ('2016822703383740417', null, null, '1', '2026-01-29 18:36:18', '2026-03-01 22:00:32', '13303820660', '13303820660', 0, 1, 'SEX', '2', '女', 1, 1, null, null, null);
