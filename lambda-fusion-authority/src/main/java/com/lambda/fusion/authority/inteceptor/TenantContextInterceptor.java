package com.lambda.fusion.authority.inteceptor;

import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.cloud.redis.helper.RedisHelper;
import com.lambda.fusion.core.FusionConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 核心租户请求拦截器
 * 用于解析 HTTP 请求头中的租户信息（如 X-Tenant-Id），并放入 TenantContextHolder 中，
 */
@Slf4j
@Component
public class TenantContextInterceptor implements HandlerInterceptor {

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private RedisHelper redisHelper;

    @Autowired
    public void setRedisHelper(RedisHelper redisHelper) {
        this.redisHelper = redisHelper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        if (StringUtils.hasText(tenantId)) {
            TenantContextHolder.getInstance().setTenantId(tenantId);
            log.debug("Extracted Tenant ID [{}] from header", tenantId);
        }

        if (!StringUtils.hasText(tenantId)) {
            String serverName = request.getServerName();
            if (StringUtils.hasText(serverName)) {
                Object cachedTenantId = redisHelper.hGet(FusionConstants.TENANT_HOST_REDIS_KEY, serverName);
                if (cachedTenantId != null && StringUtils.hasText(cachedTenantId.toString())) {
                    tenantId = cachedTenantId.toString();
                    TenantContextHolder.getInstance().setTenantId(tenantId);
                    log.debug("Resolved Tenant ID [{}] from domain [{}]", tenantId, serverName);
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex) {
        TenantContextHolder.getInstance().close();
    }
}
