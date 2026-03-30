package com.lambda.fusion.permission.client;

import cn.hutool.core.collection.CollUtil;
import com.lambda.fusion.permission.PermissionProperties;
import com.lambda.fusion.permission.loader.LocalPermissionLoader;
import com.lambda.fusion.permission.model.PermissionFileMetadata;
import com.lambda.fusion.permission.model.PermissionPushRequest;
import com.lambda.fusion.permission.service.ApiPermissionRegistry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
@RequiredArgsConstructor
public class PermissionClientInitializer implements ApplicationRunner, DisposableBean {
    private final PermissionProperties properties;
    private final LocalPermissionLoader localPermissionLoader;
    private final ApiPermissionRegistry apiPermissionRegistry;
    private final PermissionPushClient permissionPushClient;
    private final AtomicBoolean schedulerStarted = new AtomicBoolean(false);
    private final AtomicReference<List<PermissionFileMetadata>> loadedFiles = new AtomicReference<>(List.of());
    private final ScheduledExecutorService pushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "permission-client-repush");
        t.setDaemon(true);
        return t;
    });

    @Value("${spring.application.name:unknown-app}")
    private String applicationName;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        List<PermissionFileMetadata> files = localPermissionLoader.load();
        if (CollUtil.isEmpty(files)) {
            log.warn("no permission json files found, app={}", applicationName);
            return;
        }
        List<PermissionFileMetadata> safeFiles = List.copyOf(files);
        loadedFiles.set(safeFiles);
        apiPermissionRegistry.replaceLocal(safeFiles, applicationName);
        log.info("loaded {} permission json files", safeFiles.size());
        if (!properties.getClient().isPushEnabled()) {
            return;
        }
        String instanceId = buildInstanceId();
        pushOnce(safeFiles, instanceId, properties.getClient().isFailFast());
        long pushIntervalSeconds = properties.getClient().getPushIntervalSeconds();
        if (pushIntervalSeconds > 0 && schedulerStarted.compareAndSet(false, true)) {
            pushExecutor.scheduleWithFixedDelay(
                    () -> pushOnce(loadedFiles.get(), instanceId, false),
                    pushIntervalSeconds,
                    pushIntervalSeconds,
                    TimeUnit.SECONDS);
            log.info(
                    "permission metadata repush scheduled every {} seconds, app={}",
                    pushIntervalSeconds,
                    applicationName);
        }
    }

    private void pushOnce(List<PermissionFileMetadata> files, String instanceId, boolean failFast) {
        try {
            PermissionPushRequest request = new PermissionPushRequest();
            request.setApplication(applicationName);
            request.setInstanceId(instanceId);
            request.setPushedAt(System.currentTimeMillis());
            request.setFiles(files);
            permissionPushClient.push(request);
            log.info("permission metadata pushed successfully, app={}", applicationName);
        } catch (Exception e) {
            if (failFast) {
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

    @Override
    public void destroy() {
        pushExecutor.shutdownNow();
    }
}
