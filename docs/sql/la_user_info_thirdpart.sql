create table la_user_info_thirdpart
(
    USERNAME   varchar(32) not null,
    LOGIN_TYPE varchar(11) not null,
    OPEN_ID    varchar(50) not null,
    primary key (LOGIN_TYPE, OPEN_ID)
);

