package com.lambda.fusion.config;

import com.alibaba.cloud.nacos.NacosConfigAutoConfiguration;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.config.ConfigService;
import com.lambda.fusion.config.datasource.DatabaseBasedPropertySourceLocator;
import com.lambda.fusion.config.refresh.DatabaseContextRefresher;
import com.lambda.fusion.config.service.ConfigChangedService;
import com.lambda.fusion.config.service.impl.NacosConfigService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConfigProperties.class)
@MapperScan(basePackages = {"com.lambda.fusion.config.**.mapper"})
@ComponentScan(basePackageClasses = ConfigConfigure.class)
public class ConfigConfigure {

    @Bean
    @RefreshScope
    @ConditionalOnMissingBean
    public ConfigProperties.Customize defaultCustomize() {
        return new ConfigProperties.Customize();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigChangedService configChangedService() {
        return () -> {};
    }

    @Bean
    @ConditionalOnProperty(value = "lambda.fusion.config.auto-refresh.enabled", matchIfMissing = true)
    public DatabaseContextRefresher autoRefreshMonitor(
            DatabaseBasedPropertySourceLocator databaseBasedPropertySourceLocator) {
        return new DatabaseContextRefresher(databaseBasedPropertySourceLocator);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(NacosConfigAutoConfiguration.class)
    @ConditionalOnProperty(value = "spring.cloud.nacos.config.enabled", matchIfMissing = true)
    public static class NacosSupportConfigure {

        @Bean
        public NacosConfigService nacosConfigService(
                @Autowired(required = false) ConfigService configService,
                @Autowired(required = false) NacosConfigProperties properties) {
            return new NacosConfigService(configService, properties.getGroup());
        }
    }

    @Bean
    public DatabaseBasedPropertySourceLocator databaseBasedPropertySourceLocator(ConfigProperties configProperties) {
        return new DatabaseBasedPropertySourceLocator(configProperties);
    }
}
