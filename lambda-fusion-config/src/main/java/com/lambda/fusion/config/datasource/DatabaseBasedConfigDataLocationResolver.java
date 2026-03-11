package com.lambda.fusion.config.datasource;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.config.ConfigProperties;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;

public class DatabaseBasedConfigDataLocationResolver
        implements ConfigDataLocationResolver<DatabaseBasedConfigDataResource> {
    private static final String LOCATION_PREFIX = "dbconfig:";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("^\\$\\{([^:}]+)(?::([^}]*))?}$");

    @Override
    public boolean isResolvable(@NonNull ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return location.hasPrefix(LOCATION_PREFIX);
    }

    @Override
    public List<DatabaseBasedConfigDataResource> resolve(
            @NonNull ConfigDataLocationResolverContext context, @NonNull ConfigDataLocation location) {
        return resolveResources(context);
    }

    @Override
    public List<DatabaseBasedConfigDataResource> resolveProfileSpecific(
            @NonNull ConfigDataLocationResolverContext context,
            @NonNull ConfigDataLocation location,
            @NonNull Profiles profiles) {
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
        resolveDataSourcePlaceholders(dataSourceProperty, binder);
        String application =
                binder.bind("spring.application.name", String.class).orElse("");
        ConfigProperties configProperties = binder.bind("lambda.fusion.config", Bindable.of(ConfigProperties.class))
                .orElseGet(ConfigProperties::new);
        return List.of(new DatabaseBasedConfigDataResource(dataSourceProperty, application, configProperties));
    }

    private void resolveDataSourcePlaceholders(DataSourceProperty dataSourceProperty, Binder binder) {
        dataSourceProperty.setDriverClassName(resolvePlaceholder(dataSourceProperty.getDriverClassName(), binder));
        dataSourceProperty.setUrl(resolvePlaceholder(dataSourceProperty.getUrl(), binder));
        dataSourceProperty.setUsername(resolvePlaceholder(dataSourceProperty.getUsername(), binder));
        dataSourceProperty.setPassword(resolvePlaceholder(dataSourceProperty.getPassword(), binder));
    }

    private String resolvePlaceholder(String value, Binder binder) {
        if (value == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return value;
        }
        String key = matcher.group(1);
        String defaultValue = matcher.group(2);
        return binder.bind(key, String.class).orElse(defaultValue != null ? defaultValue : value);
    }
}
