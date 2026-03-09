package com.lambda.fusion.config.datasource;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.config.ConfigProperties;
import java.util.List;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

public class DatabaseBasedConfigDataLocationResolver
        implements ConfigDataLocationResolver<DatabaseBasedConfigDataResource> {
    private static final String LOCATION_PREFIX = "dbconfig:";

    @Override
    public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return location.hasPrefix(LOCATION_PREFIX);
    }

    @Override
    public List<DatabaseBasedConfigDataResource> resolve(
            ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return resolveResources(context);
    }

    @Override
    public List<DatabaseBasedConfigDataResource> resolveProfileSpecific(
            ConfigDataLocationResolverContext context, ConfigDataLocation location, Profiles profiles) {
        return resolveResources(context);
    }

    private List<DatabaseBasedConfigDataResource> resolveResources(ConfigDataLocationResolverContext context) {
        Binder binder = context.getBinder();
        DataSourceProperty dataSourceProperty = binder.bind(
                        "lambda.fusion.config.datasource", Bindable.of(DataSourceProperty.class))
                .orElse(null);
        if (dataSourceProperty == null) {
            return List.of();
        }
        String application =
                binder.bind("spring.application.name", String.class).orElse("");
        ConfigProperties configProperties = binder.bind("lambda.fusion.config", Bindable.of(ConfigProperties.class))
                .orElseGet(ConfigProperties::new);
        return List.of(new DatabaseBasedConfigDataResource(dataSourceProperty, application, configProperties));
    }
}
