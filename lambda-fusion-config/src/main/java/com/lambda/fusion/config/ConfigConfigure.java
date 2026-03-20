package com.lambda.fusion.config;

import com.alibaba.cloud.nacos.NacosConfigAutoConfiguration;
import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.nacos.api.config.ConfigService;
import com.lambda.fusion.config.commons.handler.ConfigChangeHandler;
import com.lambda.fusion.config.commons.nacos.NacosConfigPublisher;
import com.lambda.fusion.config.commons.refresh.DatabaseConfigWatcher;
import com.lambda.fusion.config.commons.refresh.DatabaseContextRefresher;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(ConfigProperties.class)
@MapperScan(basePackages = {"com.lambda.fusion.config.**.mapper"})
@ComponentScan(basePackageClasses = ConfigConfigure.class)
public class ConfigConfigure {
    @Bean
    @ConditionalOnMissingBean
    public ConfigChangeHandler configChangedService() {
        return () -> {};
    }

    @Bean
    @ConditionalOnProperty(value = ConfigConstants.AUTO_REFRESH_ENABLED, matchIfMissing = true)
    public DatabaseConfigWatcher databaseConfigWatcher(Environment environment, ConfigProperties configProperties) {
        return new DatabaseConfigWatcher(environment, configProperties);
    }

    @Bean
    @ConditionalOnProperty(value = ConfigConstants.AUTO_REFRESH_ENABLED, matchIfMissing = true)
    public DatabaseContextRefresher autoRefreshMonitor(
            DatabaseConfigWatcher databaseConfigWatcher, ConfigProperties configProperties) {
        return new DatabaseContextRefresher(databaseConfigWatcher, configProperties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(NacosConfigAutoConfiguration.class)
    @ConditionalOnProperty(value = "spring.cloud.nacos.config.enabled", matchIfMissing = true)
    public static class NacosSupportConfigure {

        @Bean
        public NacosConfigPublisher nacosConfigService(
                @Autowired(required = false) ConfigService configService,
                @Autowired(required = false) NacosConfigProperties properties) {
            return new NacosConfigPublisher(configService, properties.getGroup());
        }
    }
}
