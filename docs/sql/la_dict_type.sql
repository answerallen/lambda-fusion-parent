create table la_dict_type
(
    id              varchar(32)             not null comment '主键'
        primary key,
    parent_id       varchar(32) default '0' null comment '父id，0代表顶级',
    dict_type       varchar(20)             not null comment '字典编码',
    dict_name       varchar(64)             not null comment '编码名称',
    data_type       varchar(64)             null comment '数据类型 0/null 静态类型 1 url  2 sql 3 enum',
    data_type_value varchar(1024)           null comment ' 类型参数, 最大长度512',
    notes           varchar(100)            null comment '备注',
    sort            int         default 0   null comment '排序编码',
    created_at      datetime                not null comment '创建时间',
    updated_at      datetime                null comment '更新时间',
    created_by      varchar(32)             not null comment '创建人ID',
    updated_by      varchar(32)             null comment '修改人ID',
    del_flag        tinyint(1)  default 0   not null comment '删除状态（0 正常 1删除）',
    dict_usage      int                     null,
    level           int                     null,
    PARENT_KEYS     varchar(128)            null
)
    comment '字典表注释表';

INSERT INTO lambda_cloud.la_dict_type (id, parent_id, dict_type, dict_name, data_type, data_type_value, notes, sort, created_at, updated_at, created_by, updated_by, del_flag, dict_usage, level, PARENT_KEYS) VALUES ('2015795037228474368', '0', 'SYS_BIZ_DICT', '系统字典', '0', null, null, 3, '2026-01-26 22:32:43', '2026-01-27 12:07:41', '13303820660', '13303820660', 0, 0, 1, null);
INSERT INTO lambda_cloud.la_dict_type (id, parent_id, dict_type, dict_name, data_type, data_type_value, notes, sort, created_at, updated_at, created_by, updated_by, del_flag, dict_usage, level, PARENT_KEYS) VALUES ('2015795188294721536', '2015795037228474368', 'SYS_ROLE_TYPE', '角色类型', '0', null, null, 1, '2026-01-26 22:33:19', '2026-01-28 10:27:52', '13303820660', '13303820661', 0, 0, 1, '2015795037228474368');
INSERT INTO lambda_cloud.la_dict_type (id, parent_id, dict_type, dict_name, data_type, data_type_value, notes, sort, created_at, updated_at, created_by, updated_by, del_flag, dict_usage, level, PARENT_KEYS) VALUES ('2016822604289114112', '0', 'SEX', '性别', '0', null, null, 1, '2026-01-29 18:35:57', '2026-03-06 12:50:07', '13303820660', 'westboy', 0, 1, 1, null);
