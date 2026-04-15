package com.lambda.fusion.ai.commons.datasource;

import cn.hutool.core.util.StrUtil;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.datasource.commons.api.DataSourceSwitcher;
import com.lambda.fusion.datasource.commons.tenant.TenantDataSourceManager;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnClass(TenantDataSourceManager.class)
public class TenantDataSourceHelper {

    private final TenantDataSourceManager tenantDataSourceManager;
    private final AiProperties aiProperties;

    public String getDataSourceName() {
        return aiProperties.getDataSource().getName();
    }

    public String getCurrentTenantDataSourceName() {
        return tenantDataSourceManager.getCurrentTenantDataSourceName(
                getDataSourceName(), aiProperties.getDataSource().getTenantPrefix());
    }

    public String getTenantDataSourceName(String tenantId) {
        return tenantDataSourceManager.getTenantDataSourceName(
                tenantId, aiProperties.getDataSource().getTenantPrefix());
    }

    public boolean tenantDataSourceExists(String tenantId) {
        return tenantDataSourceManager.tenantDataSourceExists(
                tenantId, aiProperties.getDataSource().getTenantPrefix());
    }

    public boolean createTenantDataSource(String tenantId, RemoteDataSource dataSourceConfig) {
        return tenantDataSourceManager.createTenantDataSource(
                tenantId, aiProperties.getDataSource().getTenantPrefix(), dataSourceConfig);
    }

    public boolean deleteTenantDataSource(String tenantId) {
        return tenantDataSourceManager.deleteTenantDataSource(
                tenantId, aiProperties.getDataSource().getTenantPrefix());
    }

    public List<RemoteDataSource> listEnabledTenantDataSources() {
        return tenantDataSourceManager.listEnabledTenantDataSources(
                aiProperties.getDataSource().getTenantPrefix());
    }

    public void clearCache() {
        tenantDataSourceManager.clearCache();
    }

    public DataSourceSwitcher switchToTenantDataSource(String tenantId) {
        return tenantDataSourceManager.switchToTenantDataSource(
                tenantId, aiProperties.getDataSource().getTenantPrefix());
    }

    public DataSourceSwitcher switchToCurrentTenantDataSource() {
        return tenantDataSourceManager.switchToCurrentTenantDataSource(
                getDataSourceName(), aiProperties.getDataSource().getTenantPrefix());
    }

    public String resolveTargetDataSourceName(String tenantId) {
        if (StrUtil.isBlank(tenantId) || "default".equalsIgnoreCase(tenantId)) {
            return getDataSourceName();
        }
        if (!tenantDataSourceExists(tenantId)) {
            throw new AiBusinessException(
                    AiErrorCode.TENANT_DATASOURCE_NOT_FOUND, "tenantId=" + tenantId);
        }
        return getTenantDataSourceName(tenantId);
    }

    public DataSourceSwitcher switchToResolvedDataSource(String tenantId) {
        return DataSourceSwitcher.switchTo(resolveTargetDataSourceName(tenantId));
    }
}
