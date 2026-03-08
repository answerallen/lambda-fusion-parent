package com.lambda.fusion.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.fusion.permission.api.RemotePermissionReportService;
import com.lambda.fusion.permission.client.PermissionClientInitializer;
import com.lambda.fusion.permission.client.PermissionPushClient;
import com.lambda.fusion.permission.interceptor.PermissionSecureInterceptor;
import com.lambda.fusion.permission.loader.LocalPermissionLoader;
import com.lambda.fusion.permission.server.RemotePermissionReportServiceImpl;
import com.lambda.fusion.permission.service.PermissionMatchService;
import com.lambda.fusion.permission.service.PermissionRegistry;
import com.lambda.security.inteceptor.SecureInterceptor;
import org.apache.dubbo.config.spring.ServiceBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(PermissionProperties.class)
@ConditionalOnProperty(name = PermissionConstants.PREFIX + ".enabled", havingValue = "true", matchIfMissing = true)
public class PermissionConfigure {
    @Bean
    @ConditionalOnMissingBean
    public PermissionRegistry permissionRegistry() {
        return new PermissionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionMatchService permissionMatchService() {
        return new PermissionMatchService();
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalPermissionLoader localPermissionLoader(PermissionProperties properties, ObjectMapper objectMapper) {
        return new LocalPermissionLoader(properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(
            name = PermissionConstants.MODE_PROPERTY,
            havingValue = PermissionConstants.MODE_CLIENT,
            matchIfMissing = true)
    public PermissionPushClient permissionPushClient(PermissionProperties properties) {
        return new PermissionPushClient(properties);
    }

    @Bean
    @ConditionalOnProperty(
            name = PermissionConstants.MODE_PROPERTY,
            havingValue = PermissionConstants.MODE_CLIENT,
            matchIfMissing = true)
    public PermissionClientInitializer permissionClientInitializer(
            PermissionProperties properties,
            LocalPermissionLoader localPermissionLoader,
            PermissionRegistry permissionRegistry,
            PermissionPushClient permissionPushClient) {
        return new PermissionClientInitializer(
                properties, localPermissionLoader, permissionRegistry, permissionPushClient);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            name = PermissionConstants.MODE_PROPERTY,
            havingValue = PermissionConstants.MODE_CLIENT,
            matchIfMissing = true)
    public SecureInterceptor secureInterceptor(
            PermissionProperties properties,
            PermissionRegistry permissionRegistry,
            PermissionMatchService permissionMatchService) {
        return new PermissionSecureInterceptor(properties, permissionRegistry, permissionMatchService);
    }

    @Bean
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_SERVER)
    public RemotePermissionReportService permissionReportService(
            PermissionRegistry permissionRegistry, PermissionProperties properties) {
        return new RemotePermissionReportServiceImpl(permissionRegistry, properties);
    }

    @Bean
    @ConditionalOnClass(ServiceBean.class)
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_SERVER)
    public ServiceBean<RemotePermissionReportService> remotePermissionReportServiceBean(
            RemotePermissionReportService permissionReportService, ApplicationContext applicationContext) {
        ServiceBean<RemotePermissionReportService> serviceBean = new ServiceBean<>(applicationContext);
        serviceBean.setInterface(RemotePermissionReportService.class);
        serviceBean.setRef(permissionReportService);
        serviceBean.setGroup(PermissionConstants.DUBBO_GROUP);
        serviceBean.setVersion(PermissionConstants.DUBBO_VERSION);
        return serviceBean;
    }
}
