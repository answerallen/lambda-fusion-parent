package com.lambda.fusion.ai.service;

import com.lambda.fusion.ai.datasource.AiSchemaInitializer;
import com.lambda.fusion.ai.datasource.AiTenantDataSourceHelper;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * 租户 Provisioning 服务
 * <p>
 * 负责租户数据源的创建、初始化和删除。
 * 集成 Schema 初始化和错误回滚机制。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 创建租户数据源
 * RemoteDataSource config = new RemoteDataSource();
 * config.setDriverClassName("org.postgresql.Driver");
 * config.setJdbcUrl("jdbc:postgresql://localhost:5432/tenant_1001");
 * config.setUsername("postgres");
 * config.setPassword("password");
 * config.setDbType("postgresql");
 * 
 * tenantProvisioningService.provisionTenant("1001", config);
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {
    
    private final AiTenantDataSourceHelper aiTenantDataSourceHelper;
    private final AiSchemaInitializer aiSchemaInitializer;
    
    /**
     * 创建租户数据源并初始化 Schema
     * 
     * @param tenantId 租户ID
     * @param dataSourceConfig 数据源配置
     * @throws Exception 如果创建或初始化失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void provisionTenant(String tenantId, RemoteDataSource dataSourceConfig) throws Exception {
        log.info("Starting tenant provisioning for tenant: {}", tenantId);
        
        try {
            // 1. 检查租户数据源是否已存在
            if (aiTenantDataSourceHelper.tenantDataSourceExists(tenantId)) {
                log.warn("Tenant datasource already exists for tenant: {}", tenantId);
                throw new IllegalStateException("Tenant datasource already exists: " + tenantId);
            }
            
            // 2. 创建租户数据源
            boolean created = aiTenantDataSourceHelper.createTenantDataSource(tenantId, dataSourceConfig);
            if (!created) {
                throw new RuntimeException("Failed to create tenant datasource for tenant: " + tenantId);
            }
            
            log.info("Tenant datasource created successfully for tenant: {}", tenantId);
            
            // 3. 初始化 Schema
            try {
                // 注意：这里需要获取实际的 DataSource 实例
                // 在实际实现中，应该从 DynamicRoutingDataSource 中获取
                // 这里仅作为示例，实际使用时需要注入 DataSource
                DataSource dataSource = getDataSourceForTenant(tenantId);
                aiSchemaInitializer.initializeTenantSchema(tenantId, dataSource);
                
                log.info("Tenant schema initialized successfully for tenant: {}", tenantId);
                
            } catch (Exception e) {
                log.error("Failed to initialize schema for tenant: {}, rolling back datasource creation", tenantId, e);
                
                // 回滚：删除已创建的数据源
                try {
                    aiTenantDataSourceHelper.deleteTenantDataSource(tenantId);
                    log.info("Rolled back tenant datasource for tenant: {}", tenantId);
                } catch (Exception rollbackException) {
                    log.error("Failed to rollback tenant datasource for tenant: {}", tenantId, rollbackException);
                }
                
                throw new RuntimeException("Failed to initialize tenant schema for tenant: " + tenantId, e);
            }
            
            log.info("Tenant provisioning completed successfully for tenant: {}", tenantId);
            
        } catch (Exception e) {
            log.error("Tenant provisioning failed for tenant: {}", tenantId, e);
            throw e;
        }
    }
    
    /**
     * 删除租户数据源
     * 
     * @param tenantId 租户ID
     * @return true 如果删除成功
     */
    public boolean deprovisionTenant(String tenantId) {
        log.info("Starting tenant deprovisioning for tenant: {}", tenantId);
        
        try {
            // 检查租户数据源是否存在
            if (!aiTenantDataSourceHelper.tenantDataSourceExists(tenantId)) {
                log.warn("Tenant datasource does not exist for tenant: {}", tenantId);
                return false;
            }
            
            // 删除租户数据源
            boolean deleted = aiTenantDataSourceHelper.deleteTenantDataSource(tenantId);
            
            if (deleted) {
                log.info("Tenant deprovisioning completed successfully for tenant: {}", tenantId);
            } else {
                log.warn("Failed to deprovision tenant: {}", tenantId);
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.error("Tenant deprovisioning failed for tenant: {}", tenantId, e);
            return false;
        }
    }
    
    /**
     * 检查租户数据源是否存在
     * 
     * @param tenantId 租户ID
     * @return true 如果存在
     */
    public boolean isTenantProvisioned(String tenantId) {
        return aiTenantDataSourceHelper.tenantDataSourceExists(tenantId);
    }
    
    /**
     * 获取租户数据源名称
     * 
     * @param tenantId 租户ID
     * @return 数据源名称
     */
    public String getTenantDataSourceName(String tenantId) {
        return aiTenantDataSourceHelper.getTenantDataSourceName(tenantId);
    }
    
    /**
     * 获取租户数据源实例
     * 
     * 注意：这是一个占位方法，实际实现需要从 DynamicRoutingDataSource 中获取
     * 
     * @param tenantId 租户ID
     * @return DataSource 实例
     */
    private DataSource getDataSourceForTenant(String tenantId) {
        // TODO: 实际实现需要注入 DynamicRoutingDataSource 并获取对应的数据源
        // 示例代码：
        // String dsName = aiTenantDataSourceHelper.getTenantDataSourceName(tenantId);
        // return dynamicRoutingDataSource.getDataSource(dsName);
        
        throw new UnsupportedOperationException("getDataSourceForTenant not implemented yet");
    }
}
