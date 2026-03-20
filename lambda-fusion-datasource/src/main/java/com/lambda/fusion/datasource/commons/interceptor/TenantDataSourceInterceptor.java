package com.lambda.fusion.datasource.commons.interceptor;

import com.lambda.cloud.mybatis.tenant.TenantContextHolder;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.datasource.commons.api.DataSourceSwitcher;
import com.lambda.fusion.datasource.commons.tenant.TenantDataSourceManager;
import com.lambda.fusion.datasource.commons.tenant.TenantIsolationResolver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 数据源及域名增强拦截器
 * 1. 负责从当前请求域名（Host）中解析出租户 ID，并记录到 TenantContextHolder
 * 2. 根据解析到的租户 ID，检查隔离模式（IsolationMode）
 * 3. 如果是 DEDICATED（独立库）模式，自动切换数据源
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantDataSourceInterceptor implements HandlerInterceptor {
    private final TenantIsolationResolver tenantIsolationResolver;
    private final TenantDataSourceManager tenantDataSourceManager;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        // 如果存在租户 ID，处理隔离模式和数据源切换
        if (StringUtils.hasText(tenantId)) {
            FusionConstants.IsolationMode mode =
                    tenantIsolationResolver.resolve(tenantId).orElse(null);
            if (FusionConstants.IsolationMode.DEDICATED.equals(mode)) {
                DataSourceSwitcher switcher = tenantDataSourceManager.switchToTenantDataSource(tenantId, "tenant_");
                request.setAttribute(FusionConstants.SWITCHER_ATTR, switcher);
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
        Object switcherObj = request.getAttribute(FusionConstants.SWITCHER_ATTR);
        if (switcherObj instanceof DataSourceSwitcher) {
            ((DataSourceSwitcher) switcherObj).close();
        }

        // 清理租户上下文，防止线程复用导致的内存泄漏或数据串号
        TenantContextHolder.getInstance().close();
    }
}
