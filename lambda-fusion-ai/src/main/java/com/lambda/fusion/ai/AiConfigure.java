package com.lambda.fusion.ai;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.ai.datasource.AiDataSourceInterceptor;
import com.lambda.fusion.ai.datasource.AiDatabaseSchemaInitializer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.ai.**.mapper"})
@ComponentScan(basePackageClasses = AiConfigure.class)
@EnableConfigurationProperties({AiProperties.class})
public class AiConfigure {

    @Bean
    public Executor documentProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(10);
        executor.setCorePoolSize(5);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("documentProcessExecutor" + "-");
        executor.setKeepAliveSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean
    public Executor agentParallelExecutor(AiProperties aiProperties) {
        AiProperties.ParallelExecutorConfig config = aiProperties.getAgent().getParallelExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(config.getThreadNamePrefix());
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public Executor agentStreamExecutor(AiProperties aiProperties) {
        AiProperties.ParallelExecutorConfig config = aiProperties.getAgent().getParallelExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setMaxPoolSize(config.getMaxPoolSize());
        executor.setCorePoolSize(config.getCorePoolSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(config.getThreadNamePrefix() + "stream-");
        executor.setKeepAliveSeconds(config.getKeepAliveSeconds());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public MemorySaver workflowCheckpointSaver() {
        return new MemorySaver();
    }

    /**
     * 注册 AI 数据源 SQL 级拦截器，作为 {@link com.lambda.fusion.ai.datasource.AiDataSourceAspect} 之外的逃逸安全网，
     * 覆盖非事务直接 mapper 调用、虚拟线程 / ForkJoinPool / 自调用等路径。
     *
     * @param aiProperties AI 配置（取数据源名称与 enabled 开关）
     * @return MyBatis-Plus ConfigurationCustomizer
     */
    @Bean
    public ConfigurationCustomizer aiDataSourceInterceptorCustomizer(AiProperties aiProperties) {
        return configuration -> configuration.addInterceptor(new AiDataSourceInterceptor(aiProperties));
    }

    @Configuration(proxyBeanMethods = false)
    public static class LlmResilienceConfig {

        public static final String LLM_CIRCUIT_BREAKER = "llm-call";
        public static final String LLM_RETRY = "llm-call";
        public static final String LLM_RATE_LIMITER = "llm-call";

        @Bean
        public CircuitBreakerRegistry circuitBreakerRegistry() {
            return CircuitBreakerRegistry.ofDefaults();
        }

        @Bean
        public RetryRegistry retryRegistry() {
            return RetryRegistry.ofDefaults();
        }

        @Bean
        public RateLimiterRegistry rateLimiterRegistry() {
            return RateLimiterRegistry.ofDefaults();
        }

        @Bean
        public RateLimiter llmRateLimiter(RateLimiterRegistry registry) {
            RateLimiterConfig config = RateLimiterConfig.custom()
                    .limitForPeriod(60)
                    .limitRefreshPeriod(Duration.ofMinutes(1))
                    .timeoutDuration(Duration.ofSeconds(1))
                    .build();

            return registry.rateLimiter(LLM_RATE_LIMITER, config);
        }

        @Bean
        public CircuitBreaker llmCircuitBreaker(CircuitBreakerRegistry registry) {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(10)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .minimumNumberOfCalls(5)
                    .recordExceptions(
                            TimeoutException.class,
                            java.net.SocketTimeoutException.class,
                            java.io.IOException.class,
                            RuntimeException.class)
                    .build();

            return registry.circuitBreaker(LLM_CIRCUIT_BREAKER, config);
        }

        @Bean
        public Retry llmRetry(RetryRegistry registry) {
            IntervalFunction intervalFn = IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2.0);

            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(3)
                    .intervalFunction(intervalFn)
                    .retryOnException(e -> e instanceof TimeoutException || e instanceof java.io.IOException)
                    .failAfterMaxAttempts(true)
                    .build();

            return registry.retry(LLM_RETRY, config);
        }
    }

    /**
     * 应用启动后自动执行 AI Schema 初始化。
     */
    @Bean
    public ApplicationRunner DatabaseSchemaInitializer(
            ObjectProvider<AiDatabaseSchemaInitializer> schemaInitializerProvider,
            AiProperties aiProperties,
            DynamicDataSourceService dynamicDataSourceService) {
        return args -> {
            // 获取 SchemaInitializer；若 Bean 不存在则跳过全部初始化
            AiDatabaseSchemaInitializer schemaInitializer = schemaInitializerProvider.getIfAvailable();
            if (schemaInitializer == null) {
                log.warn("DatabaseSchemaInitializer not available, skipping all AI schema initialization");
                return;
            }

            // 字段级隔离模型下仅初始化共享 AI 数据源（默认 ai-postgres）
            String defaultDsName = aiProperties.getDataSource().getName();
            log.info("Starting AI schema initialization for default datasource: {}", defaultDsName);
            DataSource defaultDataSource = resolveDataSource(dynamicDataSourceService, defaultDsName);
            if (defaultDataSource != null) {
                runSchemaInit(schemaInitializer, "default", defaultDataSource);
            }
        };
    }

    /**
     * 安全获取数据源，捕获异常并记录警告，不影响其他数据源的初始化流程。
     *
     * @param service 动态数据源服务
     * @param dsName  数据源名称
     * @return DataSource 实例；不可用时返回 {@code null}
     */
    private static DataSource resolveDataSource(DynamicDataSourceService service, String dsName) {
        try {
            DataSource ds = service.getDataSource(dsName);
            if (ds == null) {
                log.warn("AI datasource '{}' is null, skipping schema initialization", dsName);
            }
            return ds;
        } catch (Exception e) {
            log.warn("AI datasource '{}' not available, skipping schema initialization: {}", dsName, e.getMessage());
            return null;
        }
    }

    /**
     * 对指定租户执行 Liquibase 迁移，单独捕获异常，不影响其他租户的初始化。
     *
     * @param initializer Schema 初始化器
     * @param tenantId    租户 ID（用于日志和 Liquibase 上下文参数）
     * @param dataSource  租户数据源
     */
    private static void runSchemaInit(AiDatabaseSchemaInitializer initializer, String tenantId, DataSource dataSource) {
        try {
            log.info("Executing AI schema initialization for tenant: {}", tenantId);
            initializer.initializeSchema(tenantId, dataSource);
            log.info("AI schema initialization completed for tenant: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to initialize AI schema for tenant: {}", tenantId, e);
        }
    }
}
