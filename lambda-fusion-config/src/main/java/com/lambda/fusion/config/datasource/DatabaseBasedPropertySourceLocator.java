package com.lambda.fusion.config.datasource;

import static com.lambda.fusion.config.ConfigConstants.DataSource.*;
import static com.lambda.fusion.config.ConfigConstants.ErrorMessages.*;
import static com.lambda.fusion.config.ConfigConstants.PropertySource.*;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.config.ConfigProperties;
import com.lambda.fusion.config.ConfigLoadException;
import com.lambda.fusion.config.utils.DataSourcePropertyUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nonnull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.*;
import org.springframework.util.ClassUtils;

@Slf4j
@Order(100)
public class DatabaseBasedPropertySourceLocator implements PropertySourceLocator {
    private volatile HikariDataSource dataSource;
    private final ReentrantReadWriteLock dataSourceLock = new ReentrantReadWriteLock();

    @Setter
    private volatile int hashcode;

    private volatile String application;

    private final ConfigProperties configProperties;

    @Autowired
    public DatabaseBasedPropertySourceLocator(ConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    @Override
    public PropertySource<?> locate(Environment environment) {
        if (ignoreRefreshProcessing(environment)) {
            log.debug("Ignoring refresh processing for environment: {}", environment.getClass().getSimpleName());
            return null;
        }

        DataSourceProperty property = getDataSourceProperty(environment);
        if (property == null) {
            log.warn(DATASOURCE_CONFIG_NOT_FOUND);
            throw new ConfigLoadException(DATASOURCE_CONFIG_NOT_FOUND);
        }

        String url = property.getUrl();
        HikariDataSource currentDataSource = initializeDataSourceIfNeeded(property);
        application = environment.getProperty(SPRING_APPLICATION_NAME, "");

        try {
            return createPropertySource(currentDataSource, url);
        } catch (ConfigLoadException e) {
            log.error("Failed to load property source during locate phase.", e);
            throw e;
        }
    }

    private boolean ignoreRefreshProcessing(Environment environment) {
        return ClassUtils.isAssignableValue(StandardEnvironment.class, environment)
                && ((StandardEnvironment) environment).getPropertySources().contains(REFRESH_ARGS_PROPERTY_SOURCE);
    }

    private DataSourceProperty getDataSourceProperty(Environment environment) {
        try {
            return DataSourcePropertyUtils.getProperty(environment);
        } catch (Exception e) {
            throw new ConfigLoadException(FAILED_TO_GET_DATASOURCE_PROPERTY, e);
        }
    }

    private HikariDataSource initializeDataSourceIfNeeded(DataSourceProperty property) {
        dataSourceLock.readLock().lock();
        try {
            if (dataSource != null) {
                return dataSource;
            }
        } finally {
            dataSourceLock.readLock().unlock();
        }

        dataSourceLock.writeLock().lock();
        try {

            if (dataSource == null) {
                HikariConfig config = createHikariConfig(property);
                dataSource = new HikariDataSource(config);
                log.debug("DataSource initialized with URL: {}", property.getUrl());
            }
            return dataSource;
        } finally {
            dataSourceLock.writeLock().unlock();
        }
    }

    private PropertySource<?> createPropertySource(HikariDataSource currentDataSource, String url) {
        try (Connection connection = currentDataSource.getConnection()) {
            DataBaseBasedPropertySource propertySource =
                    new DataBaseBasedPropertySource(DATABASE_PROPERTY_SOURCE_NAME, connection, application, configProperties);
            this.setHashcode(propertySource.getSource().toString().hashCode());
            log.debug("DataBaseBasedPropertySource has been initialized. {}", url);
            return propertySource;
        } catch (HikariPool.PoolInitializationException e) {
            throw new ConfigLoadException("Failed to initialize connection pool from [" + url + "]", e);
        } catch (SQLException e) {
            throw new ConfigLoadException("Failed to get database connection from [" + url + "]", e);
        } catch (Exception e) {
            throw new ConfigLoadException("Unexpected error while creating property source from [" + url + "]", e);
        }
    }

    private PropertySource<?> createDefaultPropertySource() {
        return new DataBaseBasedPropertySource(DATABASE_PROPERTY_SOURCE_NAME, new DatabaseBasedProperties());
    }

    public boolean changed(ConfigurableEnvironment environment) {
        dataSourceLock.readLock().lock();
        try {
            if (dataSource == null) {
                log.debug("DataSource is null, no changes detected");
                return false;
            }

            return checkAndUpdatePropertySource(environment);
        } finally {
            dataSourceLock.readLock().unlock();
        }
    }

    private boolean checkAndUpdatePropertySource(ConfigurableEnvironment environment) {
        MutablePropertySources propertySources = environment.getPropertySources();
        DataSourceProperty property = getDataSourcePropertyForChange(environment);

        boolean dataSourceChanged = isDataSourceChanged(property, dataSource);
        if (dataSourceChanged) {
            rebuildDataSource(property);
        }

        return updatePropertySourceIfChanged(propertySources, dataSourceChanged);
    }

    private DataSourceProperty getDataSourcePropertyForChange(ConfigurableEnvironment environment) {
        try {
            return DataSourcePropertyUtils.getProperty(environment);
        } catch (Exception e) {
            throw new ConfigLoadException(FAILED_TO_GET_DATASOURCE_PROPERTY_FOR_CHANGE, e);
        }
    }

    private void rebuildDataSource(DataSourceProperty property) {
        dataSourceLock.writeLock().lock();
        try {
            closeDataSourceSafely(dataSource);
            dataSource = new HikariDataSource(createHikariConfig(property));
            log.debug("DataBaseBasedPropertySource has been rebuilt. {}", dataSource.getJdbcUrl());
        } finally {
            dataSourceLock.writeLock().unlock();
        }
    }

    private boolean updatePropertySourceIfChanged(MutablePropertySources propertySources, boolean dataSourceChanged) {
        // 使用轻量级检查
        if (!dataSourceChanged && !isConfigChanged(dataSource)) {
            return false;
        }

        DataBaseBasedPropertySource propertySource = getPropertySource(dataSource);
        if (propertySource == null) {
            log.warn("Failed to create property source for change detection");
            return false;
        }

        // 这里的 hashcode 已经在 isConfigChanged 中更新了，或者在 getPropertySource 中重新计算
        // 为了安全起见，这里再次计算全量 hashcode，但实际上 isConfigChanged 已经拦截了大部分无效查询
        int newHashcode = propertySource.getSource().toString().hashCode();
        // if (dataSourceChanged || hashcode != newHashcode) { // hashcode 在 isConfigChanged 已更新
        BootstrapPropertySource<DatabaseBasedProperties> replaced = new BootstrapPropertySource<>(propertySource);
        propertySources.replace(replaced.getName(), replaced);
        hashcode = newHashcode; // 确保同步
        log.debug("PropertySource has been updated due to changes");
        return true;
        // }
        // return false;
    }

    public boolean isDataSourceChanged(DataSourceProperty property, @Nonnull HikariDataSource dataSource) {
        if (property == null) {
            return false;
        }

        return !(Objects.equals(property.getUrl(), dataSource.getJdbcUrl())
                && Objects.equals(property.getUsername(), dataSource.getUsername()));
    }

    private boolean isConfigChanged(HikariDataSource dataSource) {
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

    public HikariConfig createHikariConfig(DataSourceProperty property) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(property.getUrl());
        configuration.setUsername(property.getUsername());
        configuration.setPassword(property.getPassword());
        configuration.setDriverClassName(property.getDriverClassName());
        configuration.setMaximumPoolSize(DEFAULT_MAX_POOL_SIZE);
        configuration.setMinimumIdle(DEFAULT_MIN_IDLE);
        configuration.setConnectionTimeout(DEFAULT_CONNECTION_TIMEOUT);

        // 设置连接池名称以便于监控
        configuration.setPoolName(POOL_NAME);

        return configuration;
    }

    public DataBaseBasedPropertySource getPropertySource(HikariDataSource dataSource) {
        if (dataSource == null) {
            log.warn("DataSource is null, cannot create property source");
            return null;
        }

        try (Connection connection = dataSource.getConnection()) {
            return new DataBaseBasedPropertySource(DATABASE_PROPERTY_SOURCE_NAME, connection, application,configProperties);
        } catch (SQLException e) {
            log.warn("Failed to get connection for property source creation", e);
            return null;
        }
    }

    private void closeDataSourceSafely(HikariDataSource dataSource) {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                log.debug("DataSource closed successfully");
            } catch (Exception e) {
                log.warn("Error occurred while closing DataSource", e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        dataSourceLock.writeLock().lock();
        try {
            closeDataSourceSafely(dataSource);
            dataSource = null;
            log.info("DatabaseBasedPropertySourceLocator destroyed successfully");
        } finally {
            dataSourceLock.writeLock().unlock();
        }
    }
}
