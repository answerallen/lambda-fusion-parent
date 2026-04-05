package com.lambda.fusion.ai;

import cn.hutool.core.util.StrUtil;
import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.datasource.commons.tenant.TenantSchemaInitializer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.beetl.core.GroupTemplate;
import org.beetl.core.resource.StringTemplateResourceLoader;
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

    @Configuration(proxyBeanMethods = false)
    public static class BeetlTemplateConfig {

        @Bean
        public GroupTemplate groupTemplate() throws IOException {
            StringTemplateResourceLoader resourceLoader = new StringTemplateResourceLoader();

            org.beetl.core.Configuration cfg = org.beetl.core.Configuration.defaultConfiguration();

            cfg.setHtmlTagSupport(false);

            cfg.setPlaceholderStart("${");
            cfg.setPlaceholderEnd("}");

            GroupTemplate gt = new GroupTemplate(resourceLoader, cfg);
            gt.registerFunctionPackage("str", new StrUtil());

            return gt;
        }
    }

    @Bean
    public ApplicationRunner vectorStoreSchemaInitializer(
            ObjectProvider<TenantSchemaInitializer> schemaInitializerProvider,
            AiProperties aiProperties,
            DynamicDataSourceService dynamicDataSourceService) {
        return args -> {
            String dataSourceName = aiProperties.getDataSource().getVectorName();
            log.info("Starting vector store schema initialization for datasource: {}", dataSourceName);

            DataSource dataSource;
            try {
                dataSource = dynamicDataSourceService.getDataSource(dataSourceName);
            } catch (Exception e) {
                log.warn("Vector store datasource '{}' not available, skipping schema initialization", dataSourceName);
                return;
            }

            if (dataSource == null) {
                log.warn("Vector store datasource '{}' is null, skipping schema initialization", dataSourceName);
                return;
            }

            schemaInitializerProvider.ifAvailable(schemaInitializer -> {
                try {
                    String tenantId = "";
                    log.info("Executing schema initialization for tenant: {}", tenantId);
                    schemaInitializer.initializeSchema(tenantId, dataSource);
                    log.info("Vector store schema initialization completed successfully");
                } catch (Exception e) {
                    log.error("Failed to initialize vector store schema", e);
                }
            });
        };
    }
}
