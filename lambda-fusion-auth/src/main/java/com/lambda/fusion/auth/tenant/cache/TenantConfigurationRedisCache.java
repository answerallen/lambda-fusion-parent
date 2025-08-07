package com.lambda.fusion.auth.tenant.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 租户配置redis缓存
 */
public class TenantConfigurationRedisCache implements TenantConfigurationCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final HashOperations<String, String, Object> hashOperations;
    private final ObjectMapper objectMapper;

    public TenantConfigurationRedisCache(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
        this.objectMapper = objectMapper;
    }

    @Override
    public void removeConfigCache(String tenantId) {
        redisTemplate.delete(getCacheKey(tenantId));
    }

    @SneakyThrows
    @Override
    public void addConfigCache(String tenantId, Map<String, Object> configMap) {
        hashOperations.putAll(getCacheKey(tenantId), configMap);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addConfigCache(String tenantId, JsonNode configJson) {
        addConfigCache(tenantId, objectMapper.convertValue(configJson, Map.class));
    }

    @Override
    @SneakyThrows
    public String getConfigCache(String tenantId) {
        Map<String, Object> configMap = getConfigMap(tenantId);
        return objectMapper.writeValueAsString(configMap);
    }

    @Override
    public Map<String, Object> getConfigMap(String tenantId) {
        return hashOperations.entries(getCacheKey(tenantId));
    }

    @Override
    @SneakyThrows
    public JsonNode getConfigJson(String tenantId) {
        Map<String, Object> configMap = getConfigMap(tenantId);
        return objectMapper.valueToTree(configMap);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String tenantId, String key) {
        Object value = hashOperations.get(getCacheKey(tenantId), key);
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
