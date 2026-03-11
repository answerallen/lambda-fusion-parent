package com.lambda.fusion.config.refresh;

import static com.lambda.fusion.config.ConfigConstants.DATABASE_PROPERTY_SOURCE_NAME;
import static com.lambda.fusion.config.ConfigConstants.POOL_NAME;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.config.ConfigProperties;
import com.lambda.fusion.config.datasource.DataBaseBasedPropertySource;
import com.lambda.fusion.config.datasource.DatabaseBasedProperties;
import com.lambda.fusion.config.utils.DataSourcePropertyUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class DatabaseConfigWatcher {

    private final Environment environment;
    private final ConfigurableEnvironment configurableEnvironment;
    private final ConfigProperties configProperties;
    private final String application;

    private volatile HikariDataSource dataSource;
    private final ReentrantReadWriteLock dataSourceLock = new ReentrantReadWriteLock();
    private volatile int hashcode;

    public DatabaseConfigWatcher(Environment environment, ConfigProperties configProperties) {
        this.environment = environment;
        this.configurableEnvironment =
                environment instanceof ConfigurableEnvironment ? (ConfigurableEnvironment) environment : null;
        this.configProperties = configProperties;
        this.application = environment.getProperty("spring.application.name", "");
    }

    public boolean changed() {
        DataSourceProperty property = DataSourcePropertyUtils.getProperty(environment);
        if (property == null) {
            return false;
        }

        ensureDataSource(property);
        if (!hasDatabasePropertySource()) {
            refreshPropertySource();
            return true;
        }

        if (isDataSourceChanged(property, dataSource)) {
            rebuildDataSource(property);
            refreshPropertySource();
            return true;
        }

        boolean changed = isConfigContentChanged();
        if (changed) {
            refreshPropertySource();
        }
        return changed;
    }

    private boolean hasDatabasePropertySource() {
        if (configurableEnvironment == null) {
            return false;
        }
        return configurableEnvironment.getPropertySources().contains(DATABASE_PROPERTY_SOURCE_NAME);
    }

    private void ensureDataSource(DataSourceProperty property) {
        if (dataSource != null) {
            return;
        }
        dataSourceLock.writeLock().lock();
        try {
            if (dataSource == null) {
                dataSource = createDataSource(property);
                updateHashcode();
            }
        } finally {
            dataSourceLock.writeLock().unlock();
        }
    }

    private HikariDataSource createDataSource(DataSourceProperty property) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(property.getUrl());
        configuration.setUsername(property.getUsername());
        configuration.setPassword(property.getPassword());
        configuration.setDriverClassName(property.getDriverClassName());
        ConfigProperties.DataSource dataSourceConfig = configProperties.getDataSource();
        configuration.setMaximumPoolSize(dataSourceConfig.getMaxPoolSize());
        configuration.setMinimumIdle(dataSourceConfig.getMinIdle());
        configuration.setConnectionTimeout(dataSourceConfig.getConnectionTimeout());
        configuration.setPoolName(POOL_NAME + "-Watcher");
        return new HikariDataSource(configuration);
    }

    private boolean isDataSourceChanged(DataSourceProperty property, HikariDataSource dataSource) {
        if (dataSource == null) {
            return true;
        }
        return !(Objects.equals(property.getUrl(), dataSource.getJdbcUrl())
                && Objects.equals(property.getUsername(), dataSource.getUsername()));
    }

    private void rebuildDataSource(DataSourceProperty property) {
        dataSourceLock.writeLock().lock();
        try {
            closeDataSourceSafely();
            dataSource = createDataSource(property);
            updateHashcode();
        } finally {
            dataSourceLock.writeLock().unlock();
        }
    }

    private void closeDataSourceSafely() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            } catch (Exception e) {
                log.warn("Error closing watcher datasource", e);
            }
        }
    }

    private boolean isConfigContentChanged() {
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            String checkSum = DatabaseBasedProperties.getCheckSum(connection, application, configProperties);
            int newHashcode = checkSum.hashCode();
            if (hashcode != newHashcode) {
                hashcode = newHashcode;
                return true;
            }
        } catch (SQLException e) {
            log.warn("Failed to check config changes", e);
        }
        return false;
    }

    private void updateHashcode() {
        if (dataSource == null) return;
        try (Connection connection = dataSource.getConnection()) {
            String checkSum = DatabaseBasedProperties.getCheckSum(connection, application, configProperties);
            this.hashcode = checkSum.hashCode();
        } catch (SQLException e) {
            log.warn("Failed to update initial config hashcode", e);
        }
    }

    private void refreshPropertySource() {
        if (configurableEnvironment == null || dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            DataBaseBasedPropertySource propertySource = new DataBaseBasedPropertySource(
                    DATABASE_PROPERTY_SOURCE_NAME, connection, application, configProperties);
            MutablePropertySources propertySources = configurableEnvironment.getPropertySources();
            if (propertySources.contains(propertySource.getName())) {
                propertySources.replace(propertySource.getName(), propertySource);
                return;
            }
            propertySources.addFirst(propertySource);
        } catch (Exception e) {
            log.warn("Failed to refresh database property source", e);
        }
    }

    @PreDestroy
    public void destroy() {
        closeDataSourceSafely();
    }
}
