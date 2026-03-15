package com.lambda.fusion.datasource.interceptor;

import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.cloud.redis.helper.RedisHelper;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.datasource.api.DataSourceSwitcher;
import com.lambda.fusion.datasource.tenant.TenantDataSourceManager;
import com.lambda.fusion.datasource.tenant.TenantIsolationResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 数据源及域名增强拦截器
 * 1. 负责从当前请求域名（Host）中解析出租户 ID，并记录到 TenantContextHolder
 * 2. 根据解析到的租户 ID，检查隔离模式（IsolationMode）
 * 3. 如果是 DEDICATED（独立库）模式，自动切换数据源
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantDataSourceInterceptor implements HandlerInterceptor {
    private final TenantIsolationResolver tenantIsolationResolver;
    private final TenantDataSourceManager tenantDataSourceManager;

    private static final String SWITCHER_ATTR = "TENANT_DS_SWITCHER";

    private RedisHelper redisHelper;

    @Autowired
    public void setRedisHelper(RedisHelper redisHelper) {
        this.redisHelper = redisHelper;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String tenantId = TenantContextHolder.getCurrentTenantId();

        // 1. 如果上下文还没有 tenantId，尝试从域名解析
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

        // 2. 如果存在租户 ID，处理隔离模式和数据源切换
        if (StringUtils.hasText(tenantId)) {
            FusionConstants.IsolationMode mode =
                    tenantIsolationResolver.resolve(tenantId).orElse(null);
            if (FusionConstants.IsolationMode.DEDICATED.equals(mode)) {
                DataSourceSwitcher switcher = tenantDataSourceManager.switchToTenantDataSource(tenantId, "tenant_");
                request.setAttribute(SWITCHER_ATTR, switcher);
                log.debug("Switched to DEDICATED database for tenant [{}]", tenantId);
            } else {
                log.debug("Tenant [{}] uses SHARED or undefined isolation mode, keeping default database.", tenantId);
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        // 恢复数据源
        Object switcherObj = request.getAttribute(SWITCHER_ATTR);
        if (switcherObj instanceof DataSourceSwitcher) {
            ((DataSourceSwitcher) switcherObj).close();
        }

        // 清理租户上下文，防止线程复用导致的内存泄漏或数据串号
        TenantContextHolder.getInstance().close();
    }
}
