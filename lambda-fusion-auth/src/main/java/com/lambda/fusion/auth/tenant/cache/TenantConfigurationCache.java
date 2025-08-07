package com.lambda.fusion.auth.tenant.cache;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * 租户配置缓存
 */
public interface TenantConfigurationCache {

    /**
     * 租户配置信息缓存key
     */
    String TENANT_CONFIG_CACHE_KEY = "LA:TENANT:CONFIG";

    /**
     * Remove config cache.
     *
     * @param tenantId the tenant id
     */
    void removeConfigCache(String tenantId);

    /**
     * Add config cache.
     *
     * @param tenantId  the tenant id
     * @param configMap the config map
     */
    void addConfigCache(String tenantId, Map<String, Object> configMap);

    /**
     * Add config cache.
     *
     * @param tenantId   the tenant id
     * @param configJson the config json
     */
    void addConfigCache(String tenantId, JsonNode configJson);

    /**
     * Gets config cache.
     *
     * @param tenantId the tenant id
     * @return the config cache
     */
    String getConfigCache(String tenantId);

    /**
     * 获取配置map
     *
     * @param tenantId the tenant id
     * @return the config map
     */
    Map<String, Object> getConfigMap(String tenantId);

    /**
     * 获取配置json
     *
     * @param tenantId the tenant id
     * @return the config json
     */
    JsonNode getConfigJson(String tenantId);

    /**
     * 获取配置值
     *
     * @param <T>      the type parameter
     * @param tenantId the tenant id
     * @param key      配置key
     * @return the config value
     */
    <T> T getConfigValue(String tenantId, String key);
}
