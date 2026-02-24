package com.lambda.fusion.authority.tenant.cache;

import static com.lambda.fusion.core.FusionConstants.TENANT_HOST_REDIS_KEY;

import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 租户域名映射缓存
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class TenantHostCache {

    private final RedisTemplate<String, Object> redisTemplate;

    public TenantHostCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private boolean check(String host) {
        if (StringUtils.isBlank(host)) {
            return false;
        }
        return redisTemplate.opsForHash().hasKey(TENANT_HOST_REDIS_KEY, host);
    }

    /**
     * 更新映射关系
     *
     * @param tenantId the tenant id
     * @param host     the host
     */
    public void put(String tenantId, String host) {
        if (StringUtils.isBlank(host)) {
            remove(tenantId);
            return;
        }
        if (check(host)) {
            throw AuthorityBusinessException.operationNotSupported("host: " + host + " 已存在");
        }
        redisTemplate.opsForHash().put(TENANT_HOST_REDIS_KEY, host, tenantId);
    }

    /**
     * 移除映射关系
     * @param tenantId  租户id
     */
    public void remove(String tenantId) {
        redisTemplate.opsForHash().entries(TENANT_HOST_REDIS_KEY).forEach((host, tid) -> {
            if (tenantId.equals(tid.toString())) {
                redisTemplate.opsForHash().delete(TENANT_HOST_REDIS_KEY, host.toString());
            }
        });
    }

    /**
     * 清空缓存
     */
    public void clear() {
        redisTemplate.delete(TENANT_HOST_REDIS_KEY);
    }
}
