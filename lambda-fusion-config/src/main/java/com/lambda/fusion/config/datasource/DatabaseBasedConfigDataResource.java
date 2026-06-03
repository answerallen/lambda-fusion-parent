package com.lambda.fusion.config.datasource;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.config.ConfigProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Getter;
import org.springframework.boot.context.config.ConfigDataResource;

@Getter
@SuppressFBWarnings("EI_EXPOSE_REP")
public class DatabaseBasedConfigDataResource extends ConfigDataResource {
    private final String application;
    private final ConfigProperties configProperties;
    private final DataSourceProperty dataSourceProperty;

    public DatabaseBasedConfigDataResource(
            DataSourceProperty dataSourceProperty, String application, ConfigProperties configProperties) {
        this.dataSourceProperty = dataSourceProperty;
        this.application = application;
        this.configProperties = configProperties;
    }
}
