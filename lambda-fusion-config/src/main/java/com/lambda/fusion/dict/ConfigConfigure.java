package com.lambda.fusion.dict;

import com.alibaba.cloud.nacos.NacosConfigAutoConfiguration;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.config.ConfigService;
import com.lambda.fusion.dict.core.DatabaseBasedPropertySourceLocator;
import com.lambda.fusion.dict.core.DatabaseContextRefresher;
import com.lambda.fusion.dict.service.ConfigChangedService;
import com.lambda.fusion.dict.service.impl.NacosConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfigProperties.class)
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
    public DatabaseBasedPropertySourceLocator databaseBasedPropertySourceLocator() {
        return new DatabaseBasedPropertySourceLocator();
    }
}
