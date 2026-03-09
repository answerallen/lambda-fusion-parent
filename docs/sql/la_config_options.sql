create table la_config_options
(
    OPTION_ID    varchar(20)  not null comment '主键'
        primary key,
    PROPERTY_ID  varchar(20)  not null comment '所属属性',
    OPTION_NAME  varchar(120) not null comment '选项名称',
    OPTION_VALUE varchar(120) not null comment '选项值',
    OPTION_DESC  text         null comment '选项描述',
    APPLICATION  varchar(60)  not null comment '应用程序'
)
    comment '系统属性选项';

INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('1357213151576526849', '1357213151547166722', '哈希', 'hash', '', 'public');
INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('1357213151584915458', '1357213151547166722', '历史', 'history', '', 'public');
INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('1357217417452130306', '1357217417452130305', '垂直布局', 'vertical', '', 'public');
INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('1357217417464713217', '1357217417452130305', '水平布局', 'horizontal', '', 'public');
INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('1357218018546225154', '1357218018546225153', '默认主题', 'default', '', 'public');
INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('1357218018554613762', '1357218018546225153', '夜色主题', 'night', '', 'public');
INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('1357219587496939522', '1357219587488550913', '配色方案1', '#F1F4F5', '', 'public');
INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('1357219587505328130', '1357219587488550913', '配色方案2', '#FFFFFF', '', 'public');
INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('1357219587513716738', '1357219587488550913', '配色方案3', '#FFF8E1', '', 'public');
INSERT INTO lambda_cloud.la_config_options (OPTION_ID, PROPERTY_ID, OPTION_NAME, OPTION_VALUE, OPTION_DESC, APPLICATION) VALUES ('2022548708129419266', '2022548707936481281', '22222222', '2222222', '222222', 'lambda-fusion-admin');
