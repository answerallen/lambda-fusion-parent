create table la_api_token
(
    ID              varchar(20)                        not null comment 'ID'
        primary key,
    API_TOKEN       varchar(32)                        not null comment 'Api Token',
    DESCRIPTION     varchar(255)                       null comment '描述',
    IP_WHITE_LIST   varchar(256)                       null comment 'IP白名单，多个用'',''分割',
    ENABLED         int      default 1                 not null comment '是否可用 1 启用',
    CREATE_TIME     datetime default CURRENT_TIMESTAMP not null comment '创建日期',
    EXPIRATION_TIME datetime default CURRENT_TIMESTAMP not null comment '失效日期'
)
    comment 'Api Token授权信息';

