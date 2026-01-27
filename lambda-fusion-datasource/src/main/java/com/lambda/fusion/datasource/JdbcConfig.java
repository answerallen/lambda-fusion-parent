package com.lambda.fusion.datasource;

public interface JdbcConfig {
    String getJdbcUrl();

    String getUsername();

    String getPassword();

    String getDriverClassName();
}
