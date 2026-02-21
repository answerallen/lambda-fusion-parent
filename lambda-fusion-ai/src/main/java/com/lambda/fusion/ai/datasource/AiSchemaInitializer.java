package com.lambda.fusion.ai.datasource;

import com.lambda.fusion.autoconfig.AiProperties;
import com.lambda.fusion.datasource.util.DataSourceSwitcher;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * AI 模块 Schema 初始化器
 * <p>
 * 负责初始化租户数据源的数据库 Schema，包括：
 * 1. 检测数据库类型（PostgreSQL/MySQL）
 * 2. 初始化 pgvector 扩展（仅 PostgreSQL）
 * 3. 执行 Liquibase 数据库迁移
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 初始化租户 Schema
 * try {
 *     aiSchemaInitializer.initializeTenantSchema("1001", dataSource);
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
public class AiSchemaInitializer {
    
    private final AiProperties aiProperties;
    private final AiTenantDataSourceHelper tenantDataSourceHelper;
    
    /**
     * 初始化租户数据源的 Schema
     * 
     * @param tenantId 租户ID
     * @param dataSource 租户数据源
     */
    public void initializeTenantSchema(String tenantId, DataSource dataSource) {
        log.info("Initializing schema for tenant: {}", tenantId);
        
        try {
            // 切换到租户数据源
            try (DataSourceSwitcher switcher = tenantDataSourceHelper.switchToTenantDataSource(tenantId)) {
                
                // 1. 检测数据库类型
                String dbType = detectDatabaseType(dataSource);
                log.info("Detected database type: {} for tenant: {}", dbType, tenantId);
                
                // 2. 如果是 PostgreSQL，初始化 pgvector 扩展
                if ("postgresql".equalsIgnoreCase(dbType)) {
                    initializePgVector(dataSource);
                    log.info("Initialized pgvector extension for tenant: {}", tenantId);
                }
                
                // 3. 执行 Liquibase 数据库迁移
                executeLiquibaseMigration(dataSource, tenantId);
                log.info("Executed Liquibase migration for tenant: {}", tenantId);
            }
            
            log.info("Schema initialization completed for tenant: {}", tenantId);
            
        } catch (Exception e) {
            log.error("Failed to initialize schema for tenant: {}", tenantId, e);
            throw new RuntimeException("Schema initialization failed for tenant: " + tenantId, e);
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
            String productName = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName()
            );
            
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
     * 初始化 pgvector 扩展
     * 
     * @param dataSource PostgreSQL 数据源
     */
    private void initializePgVector(DataSource dataSource) {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            
            // 创建 pgvector 扩展（如果不存在）
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            
            log.info("pgvector extension initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize pgvector extension", e);
            throw new RuntimeException("Failed to initialize pgvector extension", e);
        }
    }
    
    /**
     * 执行 Liquibase 数据库迁移
     * 
     * @param dataSource 数据源
     * @param tenantId 租户ID
     */
    private void executeLiquibaseMigration(DataSource dataSource, String tenantId) {
        try {
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(dataSource);
            liquibase.setChangeLog("classpath:db/changelog/ai-changelog-master.xml");
            liquibase.setContexts("ai");
            liquibase.setDefaultSchema(null); // 使用默认 schema
            liquibase.setShouldRun(true);
            
            // 设置 Liquibase 参数
            liquibase.setChangeLogParameters(java.util.Map.of(
                "tenantId", tenantId
            ));
            
            // 执行迁移
            liquibase.afterPropertiesSet();
            
            log.info("Liquibase migration executed successfully for tenant: {}", tenantId);
            
        } catch (Exception e) {
            log.error("Failed to execute Liquibase migration for tenant: {}", tenantId, e);
            throw new RuntimeException("Failed to execute Liquibase migration for tenant: " + tenantId, e);
        }
    }
}
