package com.lambda.fusion.configs.core;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.*;
import org.springframework.util.ClassUtils;

@Slf4j
@Order(100)
public class DatabaseBasedPropertySourceLocator implements PropertySourceLocator {
    public static final String PROPERTY_SOURCE = "DataBaseBasedPropertySource";
    private static final String REFRESH_ARGS_PROPERTY_SOURCE = "refreshArgs";

    private HikariDataSource dataSource;

    @Setter
    private int hashcode;

    private String application;

    @Override
    public PropertySource<?> locate(Environment environment) {
        if (ignoreRefreshProcessing(environment)) {
            return null;
        }
        DataSourceProperty property = DataSourcePropertyHelper.getProperty(environment);
        if (property == null) {
            log.warn(
                    "Could not find dataSource configuration, ignored. More details: https://tiamaes.yuque.com/tiam/gp7ci8/zqie8r");
            return null;
        }
        String url = property.getUrl();
        if (dataSource == null) {
            HikariConfig config = getHikariConfig(property);
            dataSource = new HikariDataSource(config);
        }
        application = environment.getProperty("");
        try (Connection connection = dataSource.getConnection()) {
            DataBaseBasedPropertySource propertySource =
                    new DataBaseBasedPropertySource(PROPERTY_SOURCE, connection, application);
            this.setHashcode(propertySource.getSource().toString().hashCode());
            log.debug("DataBaseBasedPropertySource has been initialized. {}", url);
            return propertySource;
        } catch (HikariPool.PoolInitializationException | SQLException e) {
            log.warn("Failed to initialize the DatabaseBasedProperties from [" + url + " ], use the default instead");
            return new DataBaseBasedPropertySource(PROPERTY_SOURCE, new DatabaseBasedProperties());
        }
    }

    private boolean ignoreRefreshProcessing(Environment environment) {
        return ClassUtils.isAssignableValue(StandardEnvironment.class, environment)
                && ((StandardEnvironment) environment).getPropertySources().contains(REFRESH_ARGS_PROPERTY_SOURCE);
    }

    public boolean changed(ConfigurableEnvironment environment) {
        boolean changed = false;
        MutablePropertySources propertySources = environment.getPropertySources();
        Objects.requireNonNull(dataSource);
        DataSourceProperty property = DataSourcePropertyUtils.getProperty(environment);
        boolean dataSourceChanged = isDataSourceChanged(property, dataSource);
        if (dataSourceChanged) {
            dataSource.close();
            dataSource = new HikariDataSource(getHikariConfig(property));
            log.debug("DataBaseBasedPropertySource will been rebuild. {}", dataSource.getJdbcUrl());
        }
        DataBaseBasedPropertySource propertySource = getPropertySource(dataSource);
        if (propertySource != null) {
            int hashcode2 = propertySource.getSource().toString().hashCode();
            if (dataSourceChanged || hashcode != hashcode2) {
                BootstrapPropertySource<DatabaseBasedProperties> replaced =
                        new BootstrapPropertySource<>(propertySource);
                propertySources.replace(replaced.getName(), replaced);
                hashcode = hashcode2;
                changed = true;
            }
        }
        return changed;
    }

    public boolean isDataSourceChanged(DataSourceProperty property, @Nonnull HikariDataSource dataSource) {
        return !(Objects.nonNull(property)
                && Objects.equals(property.getUrl(), dataSource.getJdbcUrl())
                && Objects.equals(property.getUsername(), dataSource.getUsername()));
    }

    public HikariConfig getHikariConfig(DataSourceProperty property) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(property.getUrl());
        configuration.setUsername(property.getUsername());
        configuration.setPassword(property.getPassword());
        configuration.setDriverClassName(property.getDriverClassName());
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(1);
        configuration.setConnectionTimeout(3000);
        return configuration;
    }

    public DataBaseBasedPropertySource getPropertySource(HikariDataSource dataSource) {
        Objects.requireNonNull(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            return new DataBaseBasedPropertySource(PROPERTY_SOURCE, connection, application);
        } catch (SQLException e) {
            return null;
        }
    }
}
