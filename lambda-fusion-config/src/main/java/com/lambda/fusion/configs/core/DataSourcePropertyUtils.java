package com.lambda.fusion.configs.core;

import com.lambda.cloud.datasource.property.DataSourceProperty;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang.StringUtils;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.Environment;

import static com.lambda.fusion.configs.ConfigConstants.DataSource.*;

/**
 * DataSourcePropertyUtils
 *
 * @author Jin
 */
@UtilityClass
public class DataSourcePropertyUtils {

    /**
     * 获取数据源配置
     *
     * @param environment 环境
     * @return DataSourceProperty
     */
    public static DataSourceProperty getProperty(Environment environment) {
        if (enableConfigIndependentDataSource(environment)) {
            return buildConfigIndependentDataSourceProperty(environment);
        }
        return DataSourcePropertyUtils.getProperty(environment);
    }

    /**
     * 是否启用独立数据源
     *
     * @param environment 环境
     * @return boolean
     */
    private static boolean enableConfigIndependentDataSource(@Nonnull Environment environment) {
        return StringUtils.isNotBlank(environment.getProperty(CONFIG_DATASOURCE_URL));
    }

    /**
     * 构建独立数据源配置参数
     *
     * @param environment 环境
     * @return DataSourceProperty
     */
    private static DataSourceProperty buildConfigIndependentDataSourceProperty(Environment environment) {
        PlaceholdersResolver resolver = new PropertySourcesPlaceholdersResolver(environment);
        Binder binder = new Binder(ConfigurationPropertySources.get(environment), resolver);
        BindResult<DataSourceProperty> bindResult =
                binder.bind(CONFIG_DATASOURCE_PREFIX, DataSourceProperty.class);
        return bindResult.get();
    }
}
