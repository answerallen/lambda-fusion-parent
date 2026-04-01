package com.lambda.fusion.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@EnableAsync
@Configuration
@MapperScan(basePackages = {"com.lambda.fusion.ai.**.mapper"})
@ComponentScan(basePackageClasses = AiConfigure.class)
@EnableConfigurationProperties({AiProperties.class})
public class AiConfigure {
    @Bean
    public EmbeddingModel embeddingModel(AiProperties aiProperties) {
        AiProperties.EmbeddingConfig config = aiProperties.getEmbedding();

        return OpenAiEmbeddingModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .baseUrl(config.getBaseUrl())
                .build();
    }

    @Bean
    public Executor documentProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 核心线程数
        executor.setCorePoolSize(5);
        // 任务队列的大小
        executor.setQueueCapacity(1000);
        // 线程前缀名
        executor.setThreadNamePrefix("documentProcessExecutor" + "-");
        // 线程存活时间
        executor.setKeepAliveSeconds(30);
        /*
         * 拒绝处理策略
         * CallerRunsPolicy()：交由调用方线程运行，比如 main 线程。
         * AbortPolicy()：直接抛出异常。
         * DiscardPolicy()：直接丢弃。
         * DiscardOldestPolicy()：丢弃队列中最老的任务。
         */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Configuration
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
}
