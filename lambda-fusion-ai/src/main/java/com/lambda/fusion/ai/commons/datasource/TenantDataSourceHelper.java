package com.lambda.fusion.ai.commons.datasource;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.datasource.commons.api.DataSourceSwitcher;
import com.lambda.fusion.datasource.commons.tenant.TenantDataSourceManager;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 模块租户数据源助手
 * <p>
 * 封装通用的 TenantDataSourceManager，提供 AI 模块特定的便捷方法。
 * 自动注入 AI 模块的配置属性，简化调用代码。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 获取租户数据源名称
 * String dsName = aiTenantDataSourceHelper.getTenantDataSourceName("1001");
 *
 * // 编程式切换到租户数据源
 * try (DataSourceSwitcher switcher =
 *         aiTenantDataSourceHelper.switchToTenantDataSource("1001")) {
 *     // 执行租户数据库操作
 *     knowledgeBaseMapper.selectList(null);
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class TenantDataSourceHelper {

    private final TenantDataSourceManager tenantDataSourceManager;
    private final AiProperties aiProperties;

    public String getCoreDataSourceName() {
        return aiProperties.getDataSource().getDefaultName();
    }

    public String getVectorDataSourceName() {
        return aiProperties.getDataSource().getVectorName();
    }

    /**
     * 获取当前租户的数据源名称
     */
    public String getCurrentTenantDataSourceName() {
        return getCurrentCoreDataSourceName();
    }

    public String getCurrentCoreDataSourceName() {
        return tenantDataSourceManager.getCurrentTenantDataSourceName(
                getCoreDataSourceName(), aiProperties.getDataSource().getTenantPrefix());
    }

    public String getCurrentVectorDataSourceName() {
        return tenantDataSourceManager.getCurrentTenantDataSourceName(
                getVectorDataSourceName(), aiProperties.getDataSource().getTenantPrefix());
    }

    /**
     * 获取指定租户的数据源名称
     */
    public String getTenantDataSourceName(String tenantId) {
        return tenantDataSourceManager.getTenantDataSourceName(
                tenantId, aiProperties.getDataSource().getTenantPrefix());
    }

    /**
     * 检查租户数据源是否存在
     */
    public boolean tenantDataSourceExists(String tenantId) {
        return tenantDataSourceManager.tenantDataSourceExists(
                tenantId, aiProperties.getDataSource().getTenantPrefix());
    }

    /**
     * 创建租户数据源
     */
    public boolean createTenantDataSource(String tenantId, RemoteDataSource dataSourceConfig) {
        return tenantDataSourceManager.createTenantDataSource(
                tenantId, aiProperties.getDataSource().getTenantPrefix(), dataSourceConfig);
    }

    /**
     * 删除租户数据源
     */
    public boolean deleteTenantDataSource(String tenantId) {
        return tenantDataSourceManager.deleteTenantDataSource(
                tenantId, aiProperties.getDataSource().getTenantPrefix());
    }

    /**
     * 获取所有已启用的租户数据源
     */
    public List<RemoteDataSource> listEnabledTenantDataSources() {
        return tenantDataSourceManager.listEnabledTenantDataSources(
                aiProperties.getDataSource().getTenantPrefix());
    }

    /**
     * 清除租户数据源缓存
     */
    public void clearCache() {
        tenantDataSourceManager.clearCache();
    }

    /**
     * 编程式切换到租户数据源
     */
    public DataSourceSwitcher switchToTenantDataSource(String tenantId) {
        return tenantDataSourceManager.switchToTenantDataSource(
                tenantId, aiProperties.getDataSource().getTenantPrefix());
    }

    /**
     * 编程式切换到当前租户数据源
     */
    public DataSourceSwitcher switchToCurrentTenantDataSource() {
        return switchToCurrentTenantCoreDataSource();
    }

    public DataSourceSwitcher switchToCurrentTenantCoreDataSource() {
        return tenantDataSourceManager.switchToCurrentTenantDataSource(
                getCoreDataSourceName(), aiProperties.getDataSource().getTenantPrefix());
    }

    public DataSourceSwitcher switchToCurrentTenantVectorDataSource() {
        return tenantDataSourceManager.switchToCurrentTenantDataSource(
                getVectorDataSourceName(), aiProperties.getDataSource().getTenantPrefix());
    }
}
