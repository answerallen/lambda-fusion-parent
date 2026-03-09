create table la_user_info
(
    USERNAME                varchar(32)   not null comment '用户编号'
        primary key,
    AVATAR                  varchar(255)  null comment '头像',
    TENANT_ID               varchar(32)   null comment '租户',
    REMARK                  varchar(255)  null comment '备注信息',
    IDENTITY_ID             varchar(18)   null comment '身份证号',
    POSITION                varchar(10)   null comment '岗位编号',
    STATUS                  varchar(2)    null comment '职工状态',
    EMP_NO                  varchar(20)   null comment '员工工号',
    DD_NO                   varchar(50)   null,
    WECHAT_NO               varchar(20)   null comment '微信账户',
    EXTEND_PARAM            varchar(512)  null comment '扩展参数',
    password_reset_required int default 1 null comment '是否需要修改密码,1代表需要;0代表不需要',
    DD_NICK                 varchar(20)   null,
    WECHAT_NAME             varchar(50)   null comment '企业微信名称'
)
    comment '用户扩展信息';

INSERT INTO lambda_cloud.la_user_info (USERNAME, AVATAR, TENANT_ID, REMARK, IDENTITY_ID, POSITION, STATUS, EMP_NO, DD_NO, WECHAT_NO, EXTEND_PARAM, password_reset_required, DD_NICK, WECHAT_NAME) VALUES ('111', null, null, null, null, null, null, null, null, null, null, 1, null, null);
INSERT INTO lambda_cloud.la_user_info (USERNAME, AVATAR, TENANT_ID, REMARK, IDENTITY_ID, POSITION, STATUS, EMP_NO, DD_NO, WECHAT_NO, EXTEND_PARAM, password_reset_required, DD_NICK, WECHAT_NAME) VALUES ('1111', null, null, null, null, null, null, null, null, null, null, 1, null, null);
INSERT INTO lambda_cloud.la_user_info (USERNAME, AVATAR, TENANT_ID, REMARK, IDENTITY_ID, POSITION, STATUS, EMP_NO, DD_NO, WECHAT_NO, EXTEND_PARAM, password_reset_required, DD_NICK, WECHAT_NAME) VALUES ('1111111', null, null, null, null, null, null, null, null, null, null, 1, null, null);
INSERT INTO lambda_cloud.la_user_info (USERNAME, AVATAR, TENANT_ID, REMARK, IDENTITY_ID, POSITION, STATUS, EMP_NO, DD_NO, WECHAT_NO, EXTEND_PARAM, password_reset_required, DD_NICK, WECHAT_NAME) VALUES ('11111112', null, null, null, null, null, null, null, null, null, null, 1, null, null);
INSERT INTO lambda_cloud.la_user_info (USERNAME, AVATAR, TENANT_ID, REMARK, IDENTITY_ID, POSITION, STATUS, EMP_NO, DD_NO, WECHAT_NO, EXTEND_PARAM, password_reset_required, DD_NICK, WECHAT_NAME) VALUES ('13303820660', null, null, '456789***000', null, null, null, null, null, null, null, 1, null, null);
INSERT INTO lambda_cloud.la_user_info (USERNAME, AVATAR, TENANT_ID, REMARK, IDENTITY_ID, POSITION, STATUS, EMP_NO, DD_NO, WECHAT_NO, EXTEND_PARAM, password_reset_required, DD_NICK, WECHAT_NAME) VALUES ('13303820661', null, null, '789000003333', null, null, null, null, null, null, null, 1, null, null);
INSERT INTO lambda_cloud.la_user_info (USERNAME, AVATAR, TENANT_ID, REMARK, IDENTITY_ID, POSITION, STATUS, EMP_NO, DD_NO, WECHAT_NO, EXTEND_PARAM, password_reset_required, DD_NICK, WECHAT_NAME) VALUES ('westboy', null, null, '0000999', null, null, null, null, null, null, null, 1, null, null);
