package com.lambda.fusion.datasource.tenant;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.proxy.TenantDataSourceProxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * 租户 Provisioning 服务抽象基类
 * <p>
 * 提供租户数据源创建、初始化和删除的通用流程。
 * 子类需要实现特定模块的配置和 Schema 初始化逻辑。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * &#64;Service
 * public class AiTenantProvisioningService extends AbstractTenantProvisioningService {
 *     
 *     private final AiSchemaInitializer aiSchemaInitializer;
 *     private final AiProperties aiProperties;
 *     
 *     &#64;Override
 *     protected TenantSchemaInitializer getSchemaInitializer() {
 *         return aiSchemaInitializer;
 *     }
 *     
 *     &#64;Override
 *     protected String getTenantPrefix() {
 *         return aiProperties.getDataSource().getTenantPrefix();
 *     }
 * }
 * </pre>
 * 
 * @author Lambda
 */
@Slf4j
public abstract class AbstractTenantProvisioningService {
    
    private final TenantDataSourceManager tenantDataSourceManager;
    private final DynamicRoutingDataSource dynamicRoutingDataSource;
    
    /**
     * 构造函数
     * 
     * @param tenantDataSourceManager 租户数据源管理器
     * @param dynamicRoutingDataSource 动态路由数据源
     */
    protected AbstractTenantProvisioningService(
            TenantDataSourceManager tenantDataSourceManager,
            DynamicRoutingDataSource dynamicRoutingDataSource) {
        this.tenantDataSourceManager = tenantDataSourceManager;
        this.dynamicRoutingDataSource = dynamicRoutingDataSource;
    }
    
    /**
     * 获取 Schema 初始化器
     * <p>
     * 子类必须实现此方法，返回特定模块的 Schema 初始化器。
     * </p>
     * 
     * @return Schema 初始化器
     */
    protected abstract TenantSchemaInitializer getSchemaInitializer();
    
    /**
     * 获取租户数据源前缀
     * <p>
     * 子类必须实现此方法，返回特定模块的租户数据源前缀。
     * 例如：AI 模块返回 "ai_tenant_"，Authority 模块返回 "auth_tenant_"
     * </p>
     * 
     * @return 租户数据源前缀
     */
    protected abstract String getTenantPrefix();
    
    /**
     * 创建租户数据源并初始化 Schema
     * <p>
     * 完整的租户配置流程：
     * 1. 检查租户数据源是否已存在
     * 2. 创建租户数据源
     * 3. 初始化 Schema（调用子类提供的初始化器）
     * 4. 如果失败，自动回滚（删除已创建的数据源）
     * </p>
     * 
     * @param tenantId 租户ID
     * @param dataSourceConfig 数据源配置
     * @throws Exception 如果创建或初始化失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void provisionTenant(String tenantId, RemoteDataSource dataSourceConfig) throws Exception {
        log.info("Starting tenant provisioning for tenant: {} with prefix: {}", tenantId, getTenantPrefix());
        
        try {
            // 1. 检查租户数据源是否已存在
            if (tenantDataSourceExists(tenantId)) {
                log.warn("Tenant datasource already exists for tenant: {}", tenantId);
                throw new IllegalStateException("Tenant datasource already exists: " + tenantId);
            }
            
            // 2. 创建租户数据源
            boolean created = createTenantDataSource(tenantId, dataSourceConfig);
            if (!created) {
                throw new RuntimeException("Failed to create tenant datasource for tenant: " + tenantId);
            }
            
            log.info("Tenant datasource created successfully for tenant: {}", tenantId);
            
            // 3. 初始化 Schema
            try {
                DataSource dataSource = getDataSourceForTenant(tenantId);
                TenantSchemaInitializer schemaInitializer = getSchemaInitializer();
                
                if (schemaInitializer != null) {
                    schemaInitializer.initializeSchema(tenantId, dataSource);
                    log.info("Tenant schema initialized successfully for tenant: {}", tenantId);
                } else {
                    log.warn("No schema initializer provided, skipping schema initialization for tenant: {}", tenantId);
                }
                
            } catch (Exception e) {
                log.error("Failed to initialize schema for tenant: {}, rolling back datasource creation", tenantId, e);
                
                // 回滚：删除已创建的数据源
                try {
                    deleteTenantDataSource(tenantId);
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
        log.info("Starting tenant deprovisioning for tenant: {} with prefix: {}", tenantId, getTenantPrefix());
        
        try {
            // 检查租户数据源是否存在
            if (!tenantDataSourceExists(tenantId)) {
                log.warn("Tenant datasource does not exist for tenant: {}", tenantId);
                return false;
            }
            
            // 删除租户数据源
            boolean deleted = deleteTenantDataSource(tenantId);
            
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
        return tenantDataSourceExists(tenantId);
    }
    
    /**
     * 获取租户数据源名称
     * 
     * @param tenantId 租户ID
     * @return 数据源名称
     */
    public String getTenantDataSourceName(String tenantId) {
        return tenantDataSourceManager.getTenantDataSourceName(tenantId, getTenantPrefix());
    }
    
    /**
     * 检查租户数据源是否存在
     * 
     * @param tenantId 租户ID
     * @return true 如果存在
     */
    protected boolean tenantDataSourceExists(String tenantId) {
        return tenantDataSourceManager.tenantDataSourceExists(tenantId, getTenantPrefix());
    }
    
    /**
     * 创建租户数据源
     * 
     * @param tenantId 租户ID
     * @param dataSourceConfig 数据源配置
     * @return true 如果创建成功
     */
    protected boolean createTenantDataSource(String tenantId, RemoteDataSource dataSourceConfig) {
        return tenantDataSourceManager.createTenantDataSource(tenantId, getTenantPrefix(), dataSourceConfig);
    }
    
    /**
     * 删除租户数据源
     * 
     * @param tenantId 租户ID
     * @return true 如果删除成功
     */
    protected boolean deleteTenantDataSource(String tenantId) {
        return tenantDataSourceManager.deleteTenantDataSource(tenantId, getTenantPrefix());
    }
    
    /**
     * 获取租户数据源实例
     * 
     * @param tenantId 租户ID
     * @return DataSource 实例
     */
    protected DataSource getDataSourceForTenant(String tenantId) {
        String dsName = getTenantDataSourceName(tenantId);
        return new TenantDataSourceProxy(dsName, tenantDataSourceManager, getTenantPrefix(), dynamicRoutingDataSource);
    }
}
