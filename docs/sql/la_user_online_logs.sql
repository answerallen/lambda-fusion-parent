create table la_user_online_logs
(
    USERNAME     varchar(32) not null comment '用户帐号',
    IP           varchar(64) null,
    device_type  varchar(32) not null comment '终端类型：0-PC_WEB',
    IS_ONLINE    tinyint(1)  null,
    ONLINE_TIME  datetime    null comment '最后一次上线时间',
    OFFLINE_TIME datetime    null comment '最后一次下线时间',
    primary key (USERNAME, device_type)
)
    comment '用户在线记录表';

INSERT INTO lambda_cloud.la_user_online_logs (USERNAME, IP, device_type, IS_ONLINE, ONLINE_TIME, OFFLINE_TIME) VALUES ('111', null, 'default', 1, '2026-03-07 21:46:50', '2026-03-07 21:46:27');
INSERT INTO lambda_cloud.la_user_online_logs (USERNAME, IP, device_type, IS_ONLINE, ONLINE_TIME, OFFLINE_TIME) VALUES ('1111', null, 'default', 0, '2026-03-07 18:58:00', '2026-03-07 21:47:51');
INSERT INTO lambda_cloud.la_user_online_logs (USERNAME, IP, device_type, IS_ONLINE, ONLINE_TIME, OFFLINE_TIME) VALUES ('13303820660', null, 'default', 1, '2026-03-09 13:45:38', '2026-03-07 22:27:20');
INSERT INTO lambda_cloud.la_user_online_logs (USERNAME, IP, device_type, IS_ONLINE, ONLINE_TIME, OFFLINE_TIME) VALUES ('13303820661', null, 'default', 0, '2026-01-27 18:51:27', '2026-01-28 13:00:39');
INSERT INTO lambda_cloud.la_user_online_logs (USERNAME, IP, device_type, IS_ONLINE, ONLINE_TIME, OFFLINE_TIME) VALUES ('westboy', null, 'default', 0, '2026-03-09 13:43:44', '2026-03-09 13:45:45');
