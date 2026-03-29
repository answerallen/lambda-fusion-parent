package com.lambda.fusion.ai.commons.datasource;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.datasource.commons.tenant.TenantDataSourceManager;
import com.lambda.fusion.datasource.commons.tenant.TenantIsolationResolver;
import com.lambda.fusion.datasource.commons.tenant.TenantSchemaInitializer;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 模块租户 Provisioning 服务
 * <p>
 * 负责 AI 模块租户数据源的创建、初始化和删除。
 * 继承通用的 AbstractTenantProvisioningService，提供 AI 特定的配置。
 * </p>
 *
 * <p>功能：</p>
 * <ul>
 *   <li>创建租户数据源</li>
 *   <li>初始化 AI Schema（pgvector 扩展、AI 表结构）</li>
 *   <li>删除租户数据源</li>
 *   <li>错误自动回滚</li>
 * </ul>
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
 * aiTenantProvisioningService.provisionTenant("1001", config);
 * </pre>
 *
 * @author Lambda
 */
@Slf4j
@Service
public class TenantProvisioningProvisioner extends AbstractTenantDataSourceProvisioner {

    private final AiSchemaInitializer aiSchemaInitializer;
    private final AiProperties aiProperties;

    /**
     * 构造函数
     *
     * @param tenantDataSourceManager 租户数据源管理器
     * @param dynamicRoutingDataSource 动态路由数据源
     * @param aiSchemaInitializer AI Schema 初始化器
     * @param aiProperties AI 配置属性
     */
    public TenantProvisioningProvisioner(
            TenantDataSourceManager tenantDataSourceManager,
            DynamicRoutingDataSource dynamicRoutingDataSource,
            TenantIsolationResolver tenantIsolationResolver,
            AiSchemaInitializer aiSchemaInitializer,
            AiProperties aiProperties) {
        super(tenantDataSourceManager, dynamicRoutingDataSource, tenantIsolationResolver);
        this.aiSchemaInitializer = aiSchemaInitializer;
        this.aiProperties = aiProperties;
    }

    /**
     * 获取 AI 模块的 Schema 初始化器
     *
     * @return AI Schema 初始化器
     */
    @Override
    protected TenantSchemaInitializer getSchemaInitializer() {
        return aiSchemaInitializer;
    }

    /**
     * 获取 AI 模块的租户数据源前缀
     *
     * @return 租户数据源前缀（例如：ai_tenant_）
     */
    @Override
    protected String getTenantPrefix() {
        return aiProperties.getDataSource().getTenantPrefix();
    }

    @Override
    public boolean supports(RemoteDataSource remoteDataSource) {
        if (remoteDataSource == null) {
            return false;
        }
        if (remoteDataSource.getTenantId() == null) {
            return false;
        }
        String datasourceName = remoteDataSource.getDatasourceName();
        if (datasourceName == null) {
            return false;
        }
        return datasourceName.startsWith(aiProperties.getDataSource().getTenantPrefix());
    }
}
