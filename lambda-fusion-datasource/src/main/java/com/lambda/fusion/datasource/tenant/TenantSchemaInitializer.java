package com.lambda.fusion.datasource.tenant;

import javax.sql.DataSource;

/**
 * 租户 Schema 初始化器接口
 * <p>
 * 定义租户数据源 Schema 初始化的标准接口。
 * 不同模块可以实现此接口来提供特定的 Schema 初始化逻辑。
 * </p>
 * 
 * <p>实现示例：</p>
 * <pre>
 * &#64;Component
 * public class AiSchemaInitializer implements TenantSchemaInitializer {
 *     
 *     &#64;Override
 *     public void initializeSchema(String tenantId, DataSource dataSource) {
 *         // 1. 初始化 pgvector 扩展
 *         // 2. 执行 Liquibase 迁移
 *         // 3. 创建 AI 特定的表结构
 *     }
 * }
 * </pre>
 * 
 * @author Lambda
 */
public interface TenantSchemaInitializer {
    
    /**
     * 初始化租户数据源的 Schema
     * <p>
     * 此方法应该包含所有必要的数据库初始化逻辑，包括：
     * - 创建数据库扩展（如 pgvector）
     * - 执行数据库迁移（Liquibase/Flyway）
     * - 创建表结构
     * - 初始化默认数据
     * </p>
     * 
     * @param tenantId 租户ID
     * @param dataSource 租户数据源
     * @throws RuntimeException 如果初始化失败
     */
    void initializeSchema(String tenantId, DataSource dataSource);
}
