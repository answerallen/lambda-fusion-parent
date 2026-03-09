create table la_configs
(
    PROPERTY_ID    varchar(20)  not null comment '主键'
        primary key,
    PROPERTY_KEY   varchar(720) not null comment '配置属性项',
    PROPERTY_VALUE text         null comment '配置属性值',
    PROPERTY_NAME  varchar(64)  not null comment '配置属性名称',
    PROPERTY_TYPE  int          not null comment '配置属性类型, 0:开关量,1:枚举值,2:字符串,3:数值',
    PROPERTY_DESC  text         null comment '配置属性描述',
    APPLICATION    varchar(60)  not null comment '应用程序',
    UPDATE_TIME    datetime     null comment '更新日期'
)
    comment '系统属性参数';

INSERT INTO lambda_cloud.la_configs (PROPERTY_ID, PROPERTY_KEY, PROPERTY_VALUE, PROPERTY_NAME, PROPERTY_TYPE, PROPERTY_DESC, APPLICATION, UPDATE_TIME) VALUES ('2022548707936481281', '8888888888888888888', '2222222', '888', 2, '888888888888', 'lambda-fusion-admin', null);
INSERT INTO lambda_cloud.la_configs (PROPERTY_ID, PROPERTY_KEY, PROPERTY_VALUE, PROPERTY_NAME, PROPERTY_TYPE, PROPERTY_DESC, APPLICATION, UPDATE_TIME) VALUES ('2022639569789988865', 'lambda-fusion-app', '2', 'spring.application.name', 4, '项目名称', 'lambda-fusion-admin', null);
