create table databasechangeloglock
(
    ID          int          not null
        primary key,
    LOCKED      tinyint      not null,
    LOCKGRANTED datetime     null,
    LOCKEDBY    varchar(255) null
);

INSERT INTO lambda_cloud.databasechangeloglock (ID, LOCKED, LOCKGRANTED, LOCKEDBY) VALUES (1, 0, null, null);
