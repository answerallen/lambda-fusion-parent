package com.lambda.fusion.authority;

import cn.dev33.satoken.listener.SaTokenListener;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.cloud.mybatis.handler.EntityMetaFiller;
import com.lambda.cloud.sse.listener.SseEventListener;
import com.lambda.fusion.authority.user.listenner.UserOnlineLogListener;
import com.lambda.fusion.authority.user.listenner.UserSeeEventListener;
import com.lambda.fusion.authority.role.service.InternalRoleService;
import com.lambda.fusion.authority.role.service.impl.InternalRoleServiceImpl;
import com.lambda.fusion.authority.tenant.TenantProperties;
import com.lambda.fusion.authority.tenant.cache.TenantConfigurationCache;
import com.lambda.fusion.authority.tenant.cache.TenantConfigurationLocalCache;
import com.lambda.fusion.authority.tenant.cache.TenantConfigurationRedisCache;
import com.lambda.fusion.authority.tenant.cache.TenantHostCache;
import com.lambda.fusion.authority.user.service.UserOnlineLogService;
import com.lambda.fusion.core.utils.LoginUserUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static com.lambda.fusion.authority.AuthorityConstants.CACHE_MANAGER;
import static com.lambda.fusion.authority.AuthorityConstants.OPERATION_LOG_EXECUTOR;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthorityProperties.class, TenantProperties.class})
public class AuthorityConfigure {

    @Bean
    public EntityMetaFiller entityMetaFiller() {
        return new EntityMetaFiller() {
            @Override
            public void insertFill(MetaObjectHandler handler, MetaObject metaObject) {
                handler.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                handler.strictInsertFill(
                        metaObject,
                        "createUser",
                        String.class,
                        LoginUserUtils.getLoginUser().getUsername());
            }

            @Override
            public void updateFill(MetaObjectHandler handler, MetaObject metaObject) {
                handler.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                handler.strictUpdateFill(
                        metaObject,
                        "updateUser",
                        String.class,
                        LoginUserUtils.getLoginUser().getUsername());
            }
        };
    }

    @Bean
    public Executor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 核心线程数
        executor.setCorePoolSize(5);
        // 任务队列的大小
        executor.setQueueCapacity(1000);
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean
    @ConditionalOnClass(SaTokenListener.class)
    public SaTokenListener userOnlineLogListener(@Autowired(required = false) UserOnlineLogService userOnlineLogService) {
        return new UserOnlineLogListener(userOnlineLogService);
    }

    @Bean
    @ConditionalOnClass(SseEventListener.class)
    public SseEventListener userSeeEventListener(@Autowired(required = false) UserOnlineLogService userOnlineLogService) {
        return new UserSeeEventListener(userOnlineLogService);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisConnectionFactory.class)
    public static class AuthorizeRedisCacheManagerConfigure {

        @Bean(CACHE_MANAGER)
        @SuppressWarnings("all")
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

    @ConditionalOnMissingBean
    @Bean
    public InternalRoleService internalRoleService() {
        return new InternalRoleServiceImpl();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    @SuppressWarnings("all")
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
