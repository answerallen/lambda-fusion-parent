package com.lambda.fusion.permission.client;

import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.model.PermissionFileMetadata;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import com.lambda.fusion.permission.service.LocalPermissionLoader;
import com.lambda.fusion.permission.service.PermissionRegistry;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@Slf4j
@RequiredArgsConstructor
public class PermissionClientInitializer implements ApplicationRunner {
    private final PermissionProperties properties;
    private final LocalPermissionLoader localPermissionLoader;
    private final PermissionRegistry permissionRegistry;
    private final PermissionPushClient permissionPushClient;

    @Value("${spring.application.name:unknown-app}")
    private String applicationName;

    @Override
    public void run(ApplicationArguments args) {
        List<PermissionFileMetadata> files = localPermissionLoader.load();
        permissionRegistry.replaceLocal(files);
        log.info("loaded {} permission json files", files.size());
        if (!properties.getClient().isPushEnabled()) {
            return;
        }
        try {
            PermissionPushRequest request = new PermissionPushRequest();
            request.setApplication(applicationName);
            request.setInstanceId(buildInstanceId());
            request.setPushedAt(System.currentTimeMillis());
            request.setFiles(files);
            permissionPushClient.push(request);
            log.info("permission metadata pushed successfully, app={}", applicationName);
        } catch (Exception e) {
            if (properties.getClient().isFailFast()) {
                throw e;
            }
            log.warn("permission metadata push failed: {}", e.getMessage());
        }
    }

    private String buildInstanceId() {
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            return applicationName + "@" + host + "#" + UUID.randomUUID();
        } catch (Exception ignored) {
            return applicationName + "#" + UUID.randomUUID();
        }
    }
}
