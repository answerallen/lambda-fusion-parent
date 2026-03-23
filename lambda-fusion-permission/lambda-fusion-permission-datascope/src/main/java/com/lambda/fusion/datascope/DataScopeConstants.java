package com.lambda.fusion.datascope;

public interface DataScopeConstants {

    String USER = "USER";

    String PREFIX = "lambda.fusion.datascope";

    enum ChangeType {
        CREATED,
        UPDATED,
        DELETED,
        MOVED
    }
}
