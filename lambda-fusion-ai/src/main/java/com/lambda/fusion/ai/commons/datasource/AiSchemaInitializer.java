package com.lambda.fusion.ai.commons.datasource;

import com.lambda.fusion.datasource.commons.tenant.TenantSchemaInitializer;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 模块 Schema 初始化器
 * <p>
 * 负责初始化租户数据源的数据库 Schema，包括：
 * 1. 检测数据库类型（PostgreSQL/MySQL）
 * 2. 执行 Liquibase 数据库迁移（主 changelog + 向量库 changelog）
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 初始化租户 Schema
 * try {
 *     aiSchemaInitializer.initializeSchema("1001", dataSource);
 *     log.info("Tenant schema initialized successfully");
 * } catch (Exception e) {
 *     log.error("Failed to initialize tenant schema", e);
 *     // 回滚数据源注册
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSchemaInitializer implements TenantSchemaInitializer {

    private static final String AI_SCHEMA_CHANGELOG_XML =
            "classpath:META-INF/db/changelogs/lambda-ai-schema-changelog.xml";
    private static final String AI_VECTOR_CHANGELOG_PATH =
            "classpath:META-INF/db/changelogs/lambda-ai-vector-changelog.xml";

    /**
     * 初始化租户数据源的 Schema
     *
     * @param tenantId   租户ID
     * @param dataSource 租户数据源
     */
    @Override
    public void initializeSchema(String tenantId, DataSource dataSource) {
        log.info("Initializing AI schema for tenant: {}", tenantId);

        try {

            // 1. 检测数据库类型
            String dbType = detectDatabaseType(dataSource);
            log.info("Detected database type: {} for tenant: {}", dbType, tenantId);

            // 2. 执行 Liquibase 数据库迁移（主 changelog）
            executeLiquibaseMigration(dataSource, AI_SCHEMA_CHANGELOG_XML, tenantId);
            log.info("Executed main Liquibase migration for tenant: {}", tenantId);

            // 3. 如果是 PostgreSQL，执行向量库专用的 changelog（包含 pgvector 扩展初始化）
            if ("postgresql".equalsIgnoreCase(dbType)) {
                executeLiquibaseMigration(dataSource, AI_VECTOR_CHANGELOG_PATH, tenantId);
                log.info("Executed vector store Liquibase migration for tenant: {}", tenantId);
            }

            log.info("AI schema initialization completed for tenant: {}", tenantId);

        } catch (Exception e) {
            log.error("Failed to initialize AI schema for tenant: {}", tenantId, e);
            throw new RuntimeException("AI schema initialization failed for tenant: " + tenantId, e);
        }
    }

    /**
     * 检测数据库类型
     *
     * @param dataSource 数据源
     * @return 数据库类型（postgresql, mysql 等）
     */
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

    /**
     * 执行 Liquibase 数据库迁移
     *
     * @param dataSource    数据源
     * @param changelogPath 变更日志路径
     * @param tenantId      租户ID
     */
    private void executeLiquibaseMigration(DataSource dataSource, String changelogPath, String tenantId) {
        try {
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(dataSource);
            liquibase.setChangeLog(changelogPath);
            liquibase.setContexts("ai");
            liquibase.setDefaultSchema(null); // 使用默认 schema
            liquibase.setShouldRun(true);

            // 设置 Liquibase 参数
            liquibase.setChangeLogParameters(java.util.Map.of("tenantId", tenantId));

            // 执行迁移
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
