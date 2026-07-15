package com.lambda.fusion.ai.datasource;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiDatabaseSchemaInitializer {

    private static final String AI_SCHEMA_CHANGELOG_XML =
            "classpath:META-INF/db/changelogs/lambda-ai-schema-changelog.xml";
    private static final String AI_VECTOR_CHANGELOG_PATH =
            "classpath:META-INF/db/changelogs/lambda-ai-vector-changelog.xml";

    public void initializeSchema(String tenantId, DataSource dataSource) {
        log.info("Initializing AI schema for tenant: {}", tenantId);

        try {
            String dbType = detectDatabaseType(dataSource);
            log.info("Detected database type: {} for tenant: {}", dbType, tenantId);

            if (!"postgresql".equalsIgnoreCase(dbType)) {
                log.error("AI schema initialization is not supported for non-PostgreSQL databases. Skipping.");
                return;
            }

            // 执行数据库迁移
            executeLiquibaseMigration(dataSource, AI_SCHEMA_CHANGELOG_XML, tenantId);

            // 执行向量数据库迁移
            executeLiquibaseMigration(dataSource, AI_VECTOR_CHANGELOG_PATH, tenantId);

            log.info("AI schema initialization completed for tenant: {}", tenantId);

        } catch (Exception e) {
            log.error("Failed to initialize AI schema for tenant: {}", tenantId, e);
            throw new RuntimeException("AI schema initialization failed for tenant: " + tenantId, e);
        }
    }

    private String detectDatabaseType(DataSource dataSource) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String productName = jdbcTemplate.execute((ConnectionCallback<String>)
                    connection -> connection.getMetaData().getDatabaseProductName());

            if (productName == null) {
                throw new IllegalStateException("Unable to detect database type");
            }

            return productName.toLowerCase();

        } catch (Exception e) {
            log.error("Failed to detect database type", e);
            throw new RuntimeException("Failed to detect database type", e);
        }
    }

    private void executeLiquibaseMigration(DataSource dataSource, String changelogPath, String tenantId) {
        try {
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(dataSource);
            liquibase.setChangeLog(changelogPath);
            liquibase.setContexts("ai");
            liquibase.setDefaultSchema(null);
            liquibase.setShouldRun(true);
            liquibase.afterPropertiesSet();

            log.info(
                    "Liquibase migration executed successfully for tenant: {}, changelog: {}", tenantId, changelogPath);

        } catch (Exception e) {
            log.error(
                    "Failed to execute Liquibase migration for tenant: {}, changelog: {}", tenantId, changelogPath, e);
            throw new RuntimeException(
                    "Failed to execute Liquibase migration for tenant: " + tenantId + ", changelog: " + changelogPath,
                    e);
        }
    }
}
