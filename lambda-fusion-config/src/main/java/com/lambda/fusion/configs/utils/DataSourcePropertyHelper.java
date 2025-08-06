package com.lambda.fusion.configs.utils;

import static com.lambda.fusion.configs.ConfigConstants.Nacos.*;

import com.alibaba.cloud.nacos.NacosPropertySourceRepository;
import com.google.common.collect.Lists;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import java.util.Collections;
import java.util.List;

import com.lambda.fusion.configs.core.DatabaseBasedEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.core.env.*;
import org.springframework.util.ClassUtils;

@Slf4j
public final class DataSourcePropertyHelper {

    private DataSourcePropertyHelper() {}

    public static DataSourceProperty getProperty(Environment environment) {
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
            environment = new DatabaseBasedEnvironment(propertySources);
        }
        return DataSourcePropertyUtils.getProperty(environment);
    }

    public static List<MapPropertySource> getNacosPropertySources() {
        ClassLoader classLoader = DataSourcePropertyHelper.class.getClassLoader();
        if (ClassUtils.isPresent(PROPERTY_SOURCE_REPOSITORY_CLASS, classLoader)) {
            return Lists.newArrayList(NacosPropertySourceRepository.getAll());
        }
        return Collections.emptyList();
    }
}
