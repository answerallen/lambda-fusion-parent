package com.lambda.fusion.config.datasource;

import static com.lambda.fusion.config.ConfigConstants.DATABASE_PROPERTY_SOURCE_NAME;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.config.exception.ConfigLoadException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.core.env.PropertySource;

public class DatabaseBasedConfigDataLoader implements ConfigDataLoader<DatabaseBasedConfigDataResource> {

    @Override
    public ConfigData load(@NonNull ConfigDataLoaderContext context, DatabaseBasedConfigDataResource resource)
            throws ConfigDataResourceNotFoundException {
        if (resource == null || resource.getDataSourceProperty() == null) {
            return null;
        }
        HikariDataSource dataSource = null;
        try {
            HikariConfig config = createHikariConfig(resource.getDataSourceProperty());
            dataSource = new HikariDataSource(config);
            PropertySource<?> propertySource = createPropertySource(dataSource, resource);
            return new ConfigData(List.of(propertySource));
        } finally {
            if (dataSource != null) {
                dataSource.close();
            }
        }
    }

    private HikariConfig createHikariConfig(DataSourceProperty property) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(property.getUrl());
        configuration.setUsername(property.getUsername());
        configuration.setPassword(property.getPassword());
        configuration.setDriverClassName(property.getDriverClassName());
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(0);
        configuration.setConnectionTimeout(10000);
        configuration.setPoolName("ConfigLoaderPool");
        return configuration;
    }

    private PropertySource<?> createPropertySource(
            HikariDataSource currentDataSource, DatabaseBasedConfigDataResource resource) {
        try (Connection connection = currentDataSource.getConnection()) {
            return new DataBaseBasedPropertySource(
                    DATABASE_PROPERTY_SOURCE_NAME,
                    connection,
                    resource.getApplication(),
                    resource.getConfigProperties());
        } catch (HikariPool.PoolInitializationException e) {
            throw ConfigLoadException.dataSourcePoolInitFailed(e);
        } catch (SQLException e) {
            throw ConfigLoadException.dataSourceConnectionFailed(e);
        } catch (Exception e) {
            throw ConfigLoadException.propertySourceCreateFailed(e);
        }
    }
}
