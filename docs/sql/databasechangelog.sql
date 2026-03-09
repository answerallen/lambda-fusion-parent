create table databasechangelog
(
    ID            varchar(255) not null,
    AUTHOR        varchar(255) not null,
    FILENAME      varchar(255) not null,
    DATEEXECUTED  datetime     not null,
    ORDEREXECUTED int          not null,
    EXECTYPE      varchar(10)  not null,
    MD5SUM        varchar(35)  null,
    DESCRIPTION   varchar(255) null,
    COMMENTS      varchar(255) null,
    TAG           varchar(255) null,
    LIQUIBASE     varchar(20)  null,
    CONTEXTS      varchar(255) null,
    LABELS        varchar(255) null,
    DEPLOYMENT_ID varchar(10)  null
);

INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-20241014001', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-01-21 22:41:23', 1, 'EXECUTED', '9:f9f501ad3617da2b5052162f9738abef', 'createTable tableName=la_dict_info', '', null, '4.31.1', null, null, '9006480187');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-20241014002', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-01-21 22:41:23', 2, 'EXECUTED', '9:2d4ca4b531b90c42fa7a26e7233c2a12', 'createTable tableName=la_dict_type', '', null, '4.31.1', null, null, '9006480187');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-202410140021', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-01-21 23:17:46', 3, 'MARK_RAN', '9:9e35c4c232ef49f5517cb12ef9c68982', 'createTable tableName=la_dict_type', '', null, '4.31.1', null, null, '9008663134');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-202410140022', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-01-21 23:19:26', 4, 'MARK_RAN', '9:0e746d2c4c3e5027d211ce1edeb16358', 'createTable tableName=la_dict_type', '', null, '4.31.1', null, null, '9008763823');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-202410140012', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-01-21 23:24:33', 5, 'MARK_RAN', '9:8265ec38eee958bbe2c2be8272a01a60', 'createTable tableName=la_dict_info', '', null, '4.31.1', null, null, '9009070769');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-202410140023', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-01-21 23:24:33', 6, 'MARK_RAN', '9:97b598e6de4bacc43a84037339f131b9', 'createTable tableName=la_dict_type', '', null, '4.31.1', null, null, '9009070769');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-202410140013', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-01-21 23:33:14', 7, 'MARK_RAN', '9:e6bf94d73e91acd8d521c9eaf0cabb2b', 'createTable tableName=la_dict_info', '', null, '4.31.1', null, null, '9009591939');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-202410140014', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-01-21 23:34:38', 8, 'MARK_RAN', '9:f6454243b98b617dcd84694938fcdb14', 'createTable tableName=la_dict_info', '', null, '4.31.1', null, null, '9009675927');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-202410140015', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-01-21 23:46:16', 9, 'MARK_RAN', '9:629c7010cc0bccf9c4ed56ee3ea6847a', 'createTable tableName=la_dict_info', '', null, '4.31.1', null, null, '9010373038');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-2024101400151', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-02-09 18:43:45', 10, 'MARK_RAN', '9:2230cf74010d29db7c193bbf2eb90adf', 'createTable tableName=la_dict_info', '', null, '4.31.1', null, null, '0633822494');
INSERT INTO lambda_cloud.databasechangelog (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, TAG, LIQUIBASE, CONTEXTS, LABELS, DEPLOYMENT_ID) VALUES ('lambda-fusion-dict-2024101400231', 'jin', 'META-INF/db/changelogs/lambda-dict-changelog.xml', '2026-02-09 18:43:46', 11, 'MARK_RAN', '9:88bb27230167611d9d66c6c9d7a2a2f9', 'createTable tableName=la_dict_type', '', null, '4.31.1', null, null, '0633822494');
