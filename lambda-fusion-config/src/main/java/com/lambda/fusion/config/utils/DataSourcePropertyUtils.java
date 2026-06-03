package com.lambda.fusion.config.utils;

import static com.lambda.fusion.config.ConfigConstants.*;

import com.alibaba.cloud.nacos.NacosPropertySourceRepository;
import com.google.common.collect.Lists;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.fusion.config.environment.DatabaseBasedEnvironment;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.*;
import org.springframework.util.ClassUtils;

/**
 * DataSourcePropertyUtils
 *
 * @author Jin
 */
@Slf4j
@UtilityClass
public class DataSourcePropertyUtils {

    /**
     * 获取数据源配置
     *
     * @param environment 环境
     * @return DataSourceProperty
     */
    public static DataSourceProperty getProperty(Environment environment) {
        Environment enhancedEnvironment = enhanceEnvironmentWithNacos(environment);

        if (enableConfigIndependentDataSource(enhancedEnvironment)) {
            return buildConfigIndependentDataSourceProperty(enhancedEnvironment);
        }

        return null;
    }

    /**
     * 使用Nacos配置增强环境
     *
     * @param environment 原始环境
     * @return 增强后的环境
     */
    private static Environment enhanceEnvironmentWithNacos(Environment environment) {
        List<MapPropertySource> nacosPropertySources = getNacosPropertySources();
        if (CollectionUtils.isNotEmpty(nacosPropertySources)) {
            MutablePropertySources propertySources = new MutablePropertySources();
            for (PropertySource<?> source : nacosPropertySources) {
                propertySources.addLast(source);
            }
            if (ClassUtils.isAssignableValue(ConfigurableEnvironment.class, environment)) {
                for (PropertySource<?> source : ((ConfigurableEnvironment) environment).getPropertySources()) {
                    propertySources.addLast(source);
                }
            }
            return new DatabaseBasedEnvironment(propertySources);
        }
        return environment;
    }

    /**
     * 获取Nacos配置源
     *
     * @return MapPropertySource列表
     */
    private static List<MapPropertySource> getNacosPropertySources() {
        ClassLoader classLoader = DataSourcePropertyUtils.class.getClassLoader();
        if (ClassUtils.isPresent(PROPERTY_SOURCE_REPOSITORY_CLASS, classLoader)) {
            return Lists.newArrayList(NacosPropertySourceRepository.getAll());
        }
        return Collections.emptyList();
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
        BindResult<DataSourceProperty> bindResult = binder.bind(CONFIG_DATASOURCE_PREFIX, DataSourceProperty.class);
        return bindResult.get();
    }
}
