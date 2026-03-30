package com.lambda.fusion.permission;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
    public PermissionPushClient permissionPushClient(
            PermissionProperties properties, PermissionSyncApi permissionSyncApi) {
        return new PermissionPushClient(properties, permissionSyncApi);
    }

    @Bean
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_CLIENT)
    public PermissionClientInitializer permissionClientInitializer(
            PermissionProperties properties,
            LocalPermissionLoader localPermissionLoader,
            ApiPermissionRegistry apiPermissionRegistry,
            PermissionPushClient permissionPushClient,
            @Value("${spring.application.name:unknown-app}") String applicationName) {
        return new PermissionClientInitializer(
                properties, localPermissionLoader, apiPermissionRegistry, permissionPushClient, applicationName);
    }

    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "org.apache.dubbo.config.spring.ReferenceBean")
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_CLIENT)
    public static class DubboClientConfiguration {

        @Bean
        @ConditionalOnMissingBean(PermissionSyncApi.class)
        public ReferenceBean<PermissionSyncApi> remoteAuthenticationServiceBean() {
            ReferenceBean<PermissionSyncApi> referenceBean = new ReferenceBean<>();
            referenceBean.setInterfaceClass(PermissionSyncApi.class);
            return referenceBean;
        }
    }

    @Bean
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_SERVER)
    public PermissionSyncApi permissionSyncApiImpl(
            ApiPermissionRegistry apiPermissionRegistry, PermissionProperties properties) {
        return new PermissionSyncApiImpl(apiPermissionRegistry, properties);
    }

    @Slf4j
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.dubbo.config.spring.ServiceBean")
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_SERVER)
    public static class DubboServiceConfiguration {

        @Bean
        @DubboService(
                interfaceClass = PermissionSyncApi.class,
                group = PermissionConstants.DUBBO_GROUP,
                version = PermissionConstants.DUBBO_VERSION)
        public PermissionSyncApi remotePermissionSyncApi(PermissionSyncApi permissionSyncApi) {
            return permissionSyncApi;
        }
    }
}
