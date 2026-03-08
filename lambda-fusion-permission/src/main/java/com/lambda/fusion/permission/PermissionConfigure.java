package com.lambda.fusion.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.fusion.permission.client.PermissionClientInitializer;
import com.lambda.fusion.permission.client.PermissionPushClient;
import com.lambda.fusion.permission.security.JsonPermissionSecureInterceptor;
import com.lambda.fusion.permission.server.PermissionReportController;
import com.lambda.fusion.permission.service.LocalPermissionLoader;
import com.lambda.fusion.permission.service.PermissionMatchService;
import com.lambda.fusion.permission.service.PermissionRegistry;
import com.lambda.security.inteceptor.SecureInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

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
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_CLIENT, matchIfMissing = true)
    public PermissionPushClient permissionPushClient(PermissionProperties properties) {
        return new PermissionPushClient(properties, new RestTemplate());
    }

    @Bean
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_CLIENT, matchIfMissing = true)
    public PermissionClientInitializer permissionClientInitializer(
            PermissionProperties properties,
            LocalPermissionLoader localPermissionLoader,
            PermissionRegistry permissionRegistry,
            PermissionPushClient permissionPushClient) {
        return new PermissionClientInitializer(properties, localPermissionLoader, permissionRegistry, permissionPushClient);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_CLIENT, matchIfMissing = true)
    public SecureInterceptor secureInterceptor(
            PermissionProperties properties,
            PermissionRegistry permissionRegistry,
            PermissionMatchService permissionMatchService) {
        return new JsonPermissionSecureInterceptor(properties, permissionRegistry, permissionMatchService);
    }

    @Bean
    @ConditionalOnProperty(name = PermissionConstants.MODE_PROPERTY, havingValue = PermissionConstants.MODE_SERVER)
    @ConditionalOnProperty(
            name = PermissionConstants.PREFIX + ".server.expose-api",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnBean(PermissionRegistry.class)
    public PermissionReportController permissionReportController(
            PermissionRegistry permissionRegistry,
            PermissionProperties properties) {
        return new PermissionReportController(permissionRegistry, properties);
    }
}
