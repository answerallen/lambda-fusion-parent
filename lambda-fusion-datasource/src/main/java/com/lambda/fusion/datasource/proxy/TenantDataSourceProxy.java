package com.lambda.fusion.datasource.proxy;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.lambda.fusion.datasource.tenant.TenantDataSourceManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 租户数据源代理（通用组件）
 * <p>
 * 用于在租户上下文中获取数据库连接
 * 确保所有连接都在正确的租户数据源上执行
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * DataSource proxy = new TenantDataSourceProxy(
 *     "ai_tenant_1001",
 *     tenantDataSourceManager,
 *     "ai_tenant_",
 *     dynamicRoutingDataSource
 * );
 * Connection connection = proxy.getConnection();
 * </pre>
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
@Slf4j
@RequiredArgsConstructor
public class TenantDataSourceProxy implements DataSource {

    private final String tenantDataSourceName;
    private final TenantDataSourceManager tenantDataSourceManager;
    private final String tenantPrefix;
    private final DynamicRoutingDataSource dynamicRoutingDataSource;

    @Override
    public Connection getConnection() throws SQLException {
        String tenantId = extractTenantIdFromDataSourceName(tenantDataSourceName);
        try (var ignored = tenantDataSourceManager.switchToTenantDataSource(tenantId, tenantPrefix)) {
            return dynamicRoutingDataSource.getConnection();
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return dynamicRoutingDataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        dynamicRoutingDataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        dynamicRoutingDataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return dynamicRoutingDataSource.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        return dynamicRoutingDataSource.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return dynamicRoutingDataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return dynamicRoutingDataSource.isWrapperFor(iface);
    }

    /**
     * 从数据源名称中提取租户ID
     *
     * @param dsName 数据源名称，格式: {tenantPrefix}{tenantId}
     * @return 租户ID
     * @throws IllegalArgumentException 如果格式无效
     */
    private String extractTenantIdFromDataSourceName(String dsName) {
        if (dsName != null && dsName.contains("_")) {
            String[] parts = dsName.split("_");
            if (parts.length >= 3) {
                return parts[parts.length - 1];
            }
        }
        throw new IllegalArgumentException("Invalid datasource name format: " + dsName);
    }
}
