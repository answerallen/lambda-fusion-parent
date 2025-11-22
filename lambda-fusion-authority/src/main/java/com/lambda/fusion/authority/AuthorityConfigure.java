package com.lambda.fusion.authority;

import static com.lambda.fusion.authority.AuthorityConstants.CACHE_MANAGER;
import static com.lambda.fusion.authority.AuthorityConstants.OPERATION_LOG_EXECUTOR;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.fusion.authority.role.service.impl.InternalRoleServiceImpl;
import com.lambda.fusion.authority.role.service.InternalRoleService;
import com.lambda.fusion.authority.tenant.TenantProperties;
import com.lambda.fusion.authority.tenant.cache.TenantConfigurationCache;
import com.lambda.fusion.authority.tenant.cache.TenantConfigurationLocalCache;
import com.lambda.fusion.authority.tenant.cache.TenantConfigurationRedisCache;
import com.lambda.fusion.authority.tenant.cache.TenantHostCache;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthorityProperties.class, TenantProperties.class})
public class AuthorityConfigure {

    @Bean
    public Executor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 核心线程数
        executor.setCorePoolSize(5);
        // 任务队列的大小
        executor.setQueueCapacity(10);
        // 线程前缀名
        executor.setThreadNamePrefix(OPERATION_LOG_EXECUTOR + "-");
        // 线程存活时间
        executor.setKeepAliveSeconds(30);
        /*
         * 拒绝处理策略
         * CallerRunsPolicy()：交由调用方线程运行，比如 main 线程。
         * AbortPolicy()：直接抛出异常。
         * DiscardPolicy()：直接丢弃。
         * DiscardOldestPolicy()：丢弃队列中最老的任务。
         */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        return executor;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisConnectionFactory.class)
    public static class AuthorizeRedisCacheManagerConfigure {

        @Bean(CACHE_MANAGER)
        public CacheManager authorityCacheManager(RedisConnectionFactory redisConnectionFactory) {
            log.debug("CacheManager: Redis");
            RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig();
            redisCacheConfiguration = redisCacheConfiguration
                    .entryTtl(Duration.ofMinutes(30L))
                    .disableCachingNullValues()
                    .serializeKeysWith(
                            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                            new GenericJackson2JsonRedisSerializer()));
            return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory))
                    .cacheDefaults(redisCacheConfiguration)
                    .build();
        }
    }

    @Bean(CACHE_MANAGER)
    @ConditionalOnMissingBean(name = CACHE_MANAGER)
    public CacheManager authorityCacheManager() {
        log.debug("CacheManager: Caffeine");
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("LAClients", "LAResourceOwners");
        //        cacheManager.setCaffeine(Caffeine.newBuilder()
        //                .initialCapacity(200)
        //                .maximumSize(500)
        //                .expireAfterWrite(30, TimeUnit.MINUTES)
        //                .recordStats());
        return cacheManager;
    }

    @ConditionalOnMissingBean
    @Bean
    public InternalRoleService internalRoleService() {
        return new InternalRoleServiceImpl();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    public static class TenantConfigurationRedisCacheConfiguration {

        @Bean
        public TenantHostCache tenantHostCache(RedisTemplate<String, Object> redisTemplate) {
            return new TenantHostCache(redisTemplate);
        }

        @Bean
        public TenantConfigurationCache tenantConfigurationRedisCache(
                RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
            return new TenantConfigurationRedisCache(redisTemplate, objectMapper);
        }
    }

    @Bean
    @ConditionalOnMissingBean(TenantConfigurationCache.class)
    public TenantConfigurationCache tenantConfigurationLocalCache() {
        return new TenantConfigurationLocalCache();
    }
}
