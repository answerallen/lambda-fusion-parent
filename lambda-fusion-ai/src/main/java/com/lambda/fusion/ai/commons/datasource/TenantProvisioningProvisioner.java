package com.lambda.fusion.ai.commons.datasource;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.datasource.commons.tenant.TenantDataSourceManager;
import com.lambda.fusion.datasource.commons.tenant.TenantIsolationResolver;
import com.lambda.fusion.datasource.commons.tenant.TenantSchemaInitializer;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnClass(TenantSchemaInitializer.class)
public class TenantProvisioningProvisioner extends AbstractTenantDataSourceProvisioner {

    private final DatabaseSchemaInitializer DatabaseSchemaInitializer;
    private final AiProperties aiProperties;

    /**
     * 构造函数
     *
     * @param tenantDataSourceManager 租户数据源管理器
     * @param dynamicRoutingDataSource 动态路由数据源
     * @param DatabaseSchemaInitializer AI Schema 初始化器
     * @param aiProperties AI 配置属性
     */
    public TenantProvisioningProvisioner(
            TenantDataSourceManager tenantDataSourceManager,
            DynamicRoutingDataSource dynamicRoutingDataSource,
            TenantIsolationResolver tenantIsolationResolver,
            DatabaseSchemaInitializer DatabaseSchemaInitializer,
            AiProperties aiProperties) {
        super(tenantDataSourceManager, dynamicRoutingDataSource, tenantIsolationResolver);
        this.DatabaseSchemaInitializer = DatabaseSchemaInitializer;
        this.aiProperties = aiProperties;
    }

    /**
     * 获取 AI 模块的 Schema 初始化器
     *
     * @return AI Schema 初始化器
     */
    @Override
    protected TenantSchemaInitializer getSchemaInitializer() {
        return DatabaseSchemaInitializer;
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
