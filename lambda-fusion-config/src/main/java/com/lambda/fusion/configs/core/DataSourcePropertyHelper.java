package com.lambda.fusion.configs.core;

import com.alibaba.cloud.nacos.NacosPropertySourceRepository;
import com.google.common.collect.Lists;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.core.env.*;
import org.springframework.util.ClassUtils;

import static com.lambda.fusion.configs.ConfigConstants.Nacos.*;

@Slf4j
public final class DataSourcePropertyHelper {

    private DataSourcePropertyHelper() {}

    /**
     * @param environment
     */
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

    // 使用常量类中定义的Nacos相关常量

    public static List<MapPropertySource> getNacosPropertySources() {
        ClassLoader classLoader = DataSourcePropertyHelper.class.getClassLoader();
        if (ClassUtils.isPresent(PROPERTY_SOURCE_REPOSITORY_CLASS, classLoader)) {
            return Lists.newArrayList(NacosPropertySourceRepository.getAll());
        }
        return Collections.emptyList();
    }
}
