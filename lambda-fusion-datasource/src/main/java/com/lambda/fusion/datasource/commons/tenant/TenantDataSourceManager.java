package com.lambda.fusion.datasource.commons.tenant;

import com.lambda.cloud.dubbo.authorize.DubboContextHolder;
import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.fusion.datasource.DatasourceConstants;
import com.lambda.fusion.datasource.commons.api.DataSourceSwitcher;
import com.lambda.fusion.datasource.commons.api.RemoteDataSourceService;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 租户数据源管理器（通用组件）
 * <p>
 * 封装 RemoteDataSourceService，提供租户数据源管理能力。
 * 支持租户数据源的命名、查询和编程式切换。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 获取租户数据源名称
 * String dsName = tenantDataSourceManager.getTenantDataSourceName("1001", "ai");
 *
 * // 编程式切换到租户数据源
 * try (DataSourceSwitcher switcher =
 *         tenantDataSourceManager.switchToTenantDataSource("1001", "ai")) {
 *     // 执行租户数据库操作
 * }
 * </pre>
 */
@Slf4j
@Component
@SuppressFBWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class TenantDataSourceManager {

    private final RemoteDataSourceService remoteDataSourceService;

    private final TenantIsolationResolver tenantIsolationResolver;

    // 租户数据源缓存: cacheKey(prefix:tenantId) -> datasourceName
    private final Map<String, String> tenantDataSourceCache = new ConcurrentHashMap<>();

    /**
     * 获取当前租户的数据源名称
     *
     * @param defaultDataSourceName 默认数据源名称（当租户ID为空或default时使用）
     * @param tenantPrefix          租户数据源名称前缀
     * @return 数据源名称
     */
    public String getCurrentTenantDataSourceName(String defaultDataSourceName, String tenantPrefix) {
        String tenantId = resolveCurrentTenantId();
        if (!StringUtils.hasText(tenantId) || "default".equals(tenantId)) {
            return defaultDataSourceName;
        }
        if (tenantIsolationResolver.isShared(tenantId)) {
            return defaultDataSourceName;
        }
        return getTenantDataSourceName(tenantId, tenantPrefix);
    }

    private String resolveCurrentTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        return DubboContextHolder.getCurrentTenantId();
    }

    /**
     * 获取指定租户的数据源名称
     *
     * @param tenantId     租户ID
     * @param tenantPrefix 租户数据源名称前缀
     * @return 数据源名称，格式: {tenantPrefix}{tenantId}
     */
    public String getTenantDataSourceName(String tenantId, String tenantPrefix) {
        String cacheKey = tenantPrefix + ":" + tenantId;
        return tenantDataSourceCache.computeIfAbsent(cacheKey, key -> tenantPrefix + tenantId);
    }

    /**
     * 检查租户数据源是否存在且已启用
     *
     * @param tenantId     租户ID
     * @param tenantPrefix 租户数据源名称前缀
     * @return true 如果数据源存在且已启用
     */
    public boolean tenantDataSourceExists(String tenantId, String tenantPrefix) {
        String dataSourceName = getTenantDataSourceName(tenantId, tenantPrefix);
        RemoteDataSource ds = remoteDataSourceService.get(dataSourceName);
        return ds != null
                && ds.getStatus() != null
                && Integer.valueOf(1).equals(ds.getStatus().getCode());
    }

    /**
     * 创建租户数据源
     *
     * @param tenantId         租户ID
     * @param tenantPrefix     租户数据源名称前缀
     * @param dataSourceConfig 数据源配置（会自动设置 datasourceName、tenantId、status）
     * @return true 如果创建成功
     */
    public boolean createTenantDataSource(String tenantId, String tenantPrefix, RemoteDataSource dataSourceConfig) {
        String dataSourceName = getTenantDataSourceName(tenantId, tenantPrefix);
        dataSourceConfig.setId(dataSourceName);
        dataSourceConfig.setDatasourceName(dataSourceName);
        dataSourceConfig.setTenantId(tenantId);
        dataSourceConfig.setStatus(DatasourceConstants.DatasourceStatus.fromCode(1));

        boolean success = remoteDataSourceService.add(dataSourceConfig);
        if (success) {
            log.info("创建租户数据源成功，名称: {}，租户: {}", dataSourceName, tenantId);
            String cacheKey = tenantPrefix + ":" + tenantId;
            tenantDataSourceCache.put(cacheKey, dataSourceName);
        }
        return success;
    }

    /**
     * 初始化租户数据源Schema
     *
     * @param tenantId     租户ID
     * @param tenantPrefix 租户数据源名称前缀
     * @return true 如果初始化成功
     */
    public boolean initTenantDataSource(String tenantId, String tenantPrefix) {
        String dataSourceName = getTenantDataSourceName(tenantId, tenantPrefix);
        RemoteDataSource ds = remoteDataSourceService.get(dataSourceName);
        if (ds == null) {
            return false;
        }
        return remoteDataSourceService.initSchema(ds.getId());
    }

    /**
     * 移除租户数据源Schema（物理销毁）
     *
     * @param tenantId     租户ID
     * @param tenantPrefix 租户数据源名称前缀
     * @return true 如果移除成功
     */
    public boolean destroyTenantDataSource(String tenantId, String tenantPrefix) {
        String dataSourceName = getTenantDataSourceName(tenantId, tenantPrefix);
        RemoteDataSource ds = remoteDataSourceService.get(dataSourceName);
        if (ds == null) {
            return false;
        }
        return remoteDataSourceService.removeSchema(ds.getId());
    }

    /**
     * 删除租户数据源
     *
     * @param tenantId     租户ID
     * @param tenantPrefix 租户数据源名称前缀
     * @return true 如果删除成功
     */
    public boolean deleteTenantDataSource(String tenantId, String tenantPrefix) {
        String dataSourceName = getTenantDataSourceName(tenantId, tenantPrefix);
        RemoteDataSource ds = remoteDataSourceService.get(dataSourceName);
        if (ds == null) {
            return false;
        }

        boolean success = remoteDataSourceService.delete(ds.getId());
        if (success) {
            log.info("删除租户数据源成功，名称: {}，租户: {}", dataSourceName, tenantId);
            String cacheKey = tenantPrefix + ":" + tenantId;
            tenantDataSourceCache.remove(cacheKey);
        }
        return success;
    }

    /**
     * 获取所有已启用的租户数据源
     *
     * @return 租户数据源列表
     */
    public List<RemoteDataSource> listEnabledTenantDataSources() {
        return remoteDataSourceService.listEnabled().stream()
                .filter(ds -> ds.getTenantId() != null)
                .toList();
    }

    public List<RemoteDataSource> listEnabledTenantDataSources(String tenantPrefix) {
        return listEnabledTenantDataSources().stream()
                .filter(ds -> StringUtils.hasText(ds.getDatasourceName()))
                .filter(ds -> !StringUtils.hasText(tenantPrefix)
                        || ds.getDatasourceName().startsWith(tenantPrefix))
                .toList();
    }

    /**
     * 清除租户数据源缓存
     */
    public void clearCache() {
        tenantDataSourceCache.clear();
        log.info("已清除租户数据源缓存");
    }

    /**
     * 编程式切换到租户数据源
     * 使用 try-with-resources 自动恢复
     *
     * @param tenantId     租户ID
     * @param tenantPrefix 租户数据源名称前缀
     * @return DataSourceSwitcher 实例，用于 try-with-resources
     */
    public DataSourceSwitcher switchToTenantDataSource(String tenantId, String tenantPrefix) {
        String dataSourceName = getTenantDataSourceName(tenantId, tenantPrefix);
        return DataSourceSwitcher.switchTo(dataSourceName);
    }

    /**
     * 编程式切换到当前租户数据源
     *
     * @param defaultDataSourceName 默认数据源名称
     * @param tenantPrefix          租户数据源名称前缀
     * @return DataSourceSwitcher 实例，用于 try-with-resources
     */
    public DataSourceSwitcher switchToCurrentTenantDataSource(String defaultDataSourceName, String tenantPrefix) {
        String dataSourceName = getCurrentTenantDataSourceName(defaultDataSourceName, tenantPrefix);
        return DataSourceSwitcher.switchTo(dataSourceName);
    }
}
