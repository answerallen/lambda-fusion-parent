package com.lambda.fusion.datasource.tenant;

import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.datasource.mapper.TenantIsolationMapper;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class TenantIsolationResolver {

    private final TenantIsolationMapper tenantIsolationMapper;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static final long TTL_MILLIS = 30_000L;

    public Optional<FusionConstants.IsolationMode> resolve(String tenantId) {
        if (!StringUtils.hasText(tenantId) || "default".equals(tenantId)) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(tenantId);
        if (cached != null && cached.expiresAt > now) {
            return Optional.ofNullable(cached.mode);
        }

        Integer code = tenantIsolationMapper.selectIsolationModeCode(tenantId);
        FusionConstants.IsolationMode mode = fromCode(code);
        cache.put(tenantId, new CacheEntry(mode, now + TTL_MILLIS));
        return Optional.ofNullable(mode);
    }

    public boolean isShared(String tenantId) {
        return resolve(tenantId).orElse(null) == FusionConstants.IsolationMode.SHARED;
    }

    public boolean isDedicated(String tenantId) {
        return resolve(tenantId).orElse(null) == FusionConstants.IsolationMode.DEDICATED;
    }

    public void clearCache() {
        cache.clear();
    }

    private static FusionConstants.IsolationMode fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (FusionConstants.IsolationMode mode : FusionConstants.IsolationMode.values()) {
            if (mode.getCode().equals(code)) {
                return mode;
            }
        }
        return null;
    }

    private static final class CacheEntry {
        private final FusionConstants.IsolationMode mode;
        private final long expiresAt;

        private CacheEntry(FusionConstants.IsolationMode mode, long expiresAt) {
            this.mode = mode;
            this.expiresAt = expiresAt;
        }
    }
}
