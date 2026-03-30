package com.lambda.fusion.permission;

import cn.dev33.satoken.stp.StpInterface;
import com.lambda.fusion.permission.api.PermissionSyncApi;
import com.lambda.fusion.permission.client.PermissionClientInitializer;
import com.lambda.fusion.permission.client.PermissionPushClient;
import com.lambda.fusion.permission.loader.LocalPermissionLoader;
import com.lambda.fusion.permission.server.PermissionSyncApiImpl;
import com.lambda.fusion.permission.service.ApiPermissionMatcher;
import com.lambda.fusion.permission.service.ApiPermissionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.ServiceBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(PermissionProperties.class)
public class PermissionConfigure {
    @Bean
    @ConditionalOnMissingBean
    public ApiPermissionRegistry permissionRegistry() {
        return new ApiPermissionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiPermissionMatcher permissionMatchService() {
        return new ApiPermissionMatcher();
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalPermissionLoader localPermissionLoader(PermissionProperties properties, ObjectMapper objectMapper) {
        return new LocalPermissionLoader(properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_CLIENT)
    public PermissionPushClient permissionPushClient(PermissionProperties properties) {
        return new PermissionPushClient(properties);
    }

    @Bean
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_CLIENT)
    public PermissionClientInitializer permissionClientInitializer(
            PermissionProperties properties,
            LocalPermissionLoader localPermissionLoader,
            ApiPermissionRegistry apiPermissionRegistry,
            PermissionPushClient permissionPushClient) {
        return new PermissionClientInitializer(
                properties, localPermissionLoader, apiPermissionRegistry, permissionPushClient);
    }

    @Bean
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_SERVER)
    public PermissionSyncApi permissionSyncApiImpl(
            ApiPermissionRegistry apiPermissionRegistry, PermissionProperties properties) {
        return new PermissionSyncApiImpl(apiPermissionRegistry, properties);
    }

    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "org.apache.dubbo.config.spring.ServiceBean")
    public static class DubboServiceConfiguration {

        @Bean
        @DubboService(interfaceClass = PermissionSyncApi.class,group = PermissionConstants.DUBBO_GROUP,version = PermissionConstants.DUBBO_VERSION)
        public PermissionSyncApi remoteAuthenticationService(PermissionSyncApi permissionSyncApi) {
            return permissionSyncApi;
        }
    }
}
