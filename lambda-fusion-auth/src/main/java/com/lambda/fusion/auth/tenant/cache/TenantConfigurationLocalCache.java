package com.lambda.fusion.auth.tenant.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.Cache;

import java.util.Map;

/**
 * 租户配置本地缓存
 */
@Slf4j
public class TenantConfigurationLocalCache implements TenantConfigurationCache {

    private  Cache cache;

    @Resource
    private ObjectMapper objectMapper;

    public TenantConfigurationLocalCache() {
//        Caffeine.newBuilder()
//                .initialCapacity(200)
//                .maximumSize(500)
//                .expireAfterWrite(30, TimeUnit.MINUTES)
//                .recordStats()
//                .build();
//        this.cache = new CaffeineCache(TENANT_CONFIG_CACHE_NAME, null);
    }

    @Override
    public void removeConfigCache(String tenantId) {
        // 清除缓存
        cache.evictIfPresent(getCacheKey(tenantId));
    }


    @SneakyThrows
    @Override
    public void addConfigCache(String tenantId, Map<String, Object> configMap) {
        // 添加缓存
        cache.put(getCacheKey(tenantId), objectMapper.writeValueAsString(configMap));
    }

    @Override
    public void addConfigCache(String tenantId, JsonNode configJson) {
        // 添加缓存
        cache.put(getCacheKey(tenantId), configJson.toString());
    }

    @Override
    public String getConfigCache(String tenantId) {
        // 获取缓存
        return cache.get(getCacheKey(tenantId), String.class);
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public Map<String, Object> getConfigMap(String tenantId) {
        String configCache = getConfigCache(tenantId);
        if (StringUtils.isBlank(configCache)) {
            return Maps.newHashMap();
        }
        return objectMapper.readValue(configCache, Map.class);
    }

    @Override
    @SneakyThrows
    public JsonNode getConfigJson(String tenantId) {
        String configCache = getConfigCache(tenantId);
        if (StringUtils.isBlank(configCache)) {
            return null;
        }
        return objectMapper.readTree(configCache);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String tenantId, String key) {
        Map<String, Object> configMap = getConfigMap(tenantId);
        Object value = configMap.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 组装缓存key
     */
    private String getCacheKey(String tenantId) {
        return TENANT_CONFIG_CACHE_KEY + ":" + tenantId;
    }
}
