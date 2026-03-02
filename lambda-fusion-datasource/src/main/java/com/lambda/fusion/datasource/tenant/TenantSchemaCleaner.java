package com.lambda.fusion.datasource.tenant;

import javax.sql.DataSource;

public interface TenantSchemaCleaner {
    void removeSchema(String tenantId, DataSource dataSource);
}
