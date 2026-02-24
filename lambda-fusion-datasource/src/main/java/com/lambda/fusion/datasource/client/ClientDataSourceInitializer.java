package com.lambda.fusion.datasource.client;

import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.cloud.datasource.property.DataSourceProperty;
import com.lambda.cloud.dubbo.authorize.DubboContextHolder;
import com.lambda.fusion.autoconfig.DatasourceProperties;
import com.lambda.fusion.datasource.api.RemoteDataSourceService;
import com.lambda.fusion.datasource.model.RemoteDataSource;
import com.lambda.fusion.datasource.util.DataSourcePropertyUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * 客户端模式初始化器 - 从远程Dubbo服务加载数据源并订阅
 *
 * <p>应用启动后异步执行，若 Dubbo 注册中心尚未就绪，按指数退避策略自动重试，
 * 直到成功或超过最大重试次数后降级运行（只打日志，不影响应用启动）。
 */
@Slf4j
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ClientDataSourceInitializer implements ApplicationRunner {

    @DubboReference(version = "1.0.0", group = "datasource", check = false)
    private RemoteDataSourceService remoteDataSourceService;

    private final DynamicDataSourceService dynamicDataSourceService;
    private final DataSourceChangeListenerImpl callback;
    private final RetryTemplate retryTemplate;

    /** 防止重复初始化（重试成功后无需再次执行） */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /** 异步执行初始化的单线程执行器，不阻塞 Spring 主启动线程 */
    private final ExecutorService initExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "datasource-client-init");
        t.setDaemon(true);
        return t;
    });

    public ClientDataSourceInitializer(
            DynamicDataSourceService dynamicDataSourceService,
            DataSourceChangeListenerImpl callback,
            DatasourceProperties datasourceProperties) {
        this.dynamicDataSourceService = dynamicDataSourceService;
        this.callback = callback;
        this.retryTemplate = buildRetryTemplate(datasourceProperties.getRetry());
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Submitting async datasource initialization (Client Mode)...");
        initExecutor.submit(this::initWithRetry);
    }

    /**
     * 带重试的初始化入口，由后台线程异步执行
     */
    private void initWithRetry() {
        try {
            retryTemplate.execute(context -> {
                int attempt = context.getRetryCount() + 1;
                log.info("Starting dynamic datasource initialization (Client Mode), attempt={}", attempt);
                doInit();
                return null;
            });
        } catch (Exception e) {
            log.error(
                    "All retry attempts for datasource initialization have been exhausted."
                            + " The application will run without remote datasources.",
                    e);
        }
    }

    /**
     * 实际执行初始化逻辑：拉取全量数据源 + 订阅变更
     */
    private void doInit() {
        // 防止重复初始化
        if (initialized.get()) {
            return;
        }

        // 1. 拉取全量已启用数据源
        List<RemoteDataSource> dataSources = remoteDataSourceService.listEnabled();
        if (dataSources != null) {
            log.info("Fetched {} remote datasources.", dataSources.size());
            for (RemoteDataSource dto : dataSources) {
                try {
                    DataSourceProperty property = DataSourcePropertyUtils.getDataSourceProperty(dto);
                    dynamicDataSourceService.addDataSource(property);
                    log.info("Loaded remote datasource: {}", dto.getDatasourceName());
                } catch (Exception e) {
                    log.error("Failed to load remote datasource: {}", dto.getDatasourceName(), e);
                }
            }
        } else {
            log.info("No remote datasources fetched.");
        }

        // 2. 订阅变更推送
        String clientId = generateClientId();
        String tenantId = DubboContextHolder.getCurrentTenantId();
        remoteDataSourceService.subscribe(clientId, tenantId, callback);
        log.info("Subscribed to remote datasource changes. ClientId: {}, TenantId: {}", clientId, tenantId);

        // 3. 注册 ShutdownHook 取消订阅
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                remoteDataSourceService.unsubscribe(clientId);
                log.info("Unsubscribed datasource changes.");
            } catch (Exception e) {
                log.warn("Failed to unsubscribe", e);
            }
        }));

        initialized.set(true);
        log.info("Dynamic datasource initialization (Client Mode) completed successfully.");
    }

    @PreDestroy
    public void shutdown() {
        initExecutor.shutdown();
    }

    private String generateClientId() {
        try {
            return InetAddress.getLocalHost().getHostAddress()
                    + ":"
                    + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception e) {
            return "unknown:" + UUID.randomUUID();
        }
    }

    /**
     * 根据配置构建 RetryTemplate（指数退避策略）
     */
    private static RetryTemplate buildRetryTemplate(DatasourceProperties.Retry retryConfig) {
        RetryTemplate template = new RetryTemplate();

        // 重试策略：最多重试 maxAttempts 次（不含首次调用）
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(retryConfig.getMaxAttempts() + 1);
        template.setRetryPolicy(retryPolicy);

        // 退避策略：指数退避，可配置初始延迟、乘数、上限
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryConfig.getInitialDelay());
        backOffPolicy.setMultiplier(retryConfig.getMultiplier());
        backOffPolicy.setMaxInterval(retryConfig.getMaxDelay());
        template.setBackOffPolicy(backOffPolicy);

        return template;
    }
}
