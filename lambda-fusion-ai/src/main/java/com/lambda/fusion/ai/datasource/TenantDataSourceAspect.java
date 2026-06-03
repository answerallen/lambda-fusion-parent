package com.lambda.fusion.ai.datasource;

import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.datasource.api.DataSourceSwitcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AI Service 数据源切面
 * <p>
 * 自动为 AI Service 层方法切换数据源：
 * <ul>
 *   <li>如果存在租户ID，切换到租户专属数据源</li>
 *   <li>否则切换到默认 AI 数据源</li>
 * </ul>
 * </p>
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class TenantDataSourceAspect {

    private final ObjectProvider<TenantDataSourceHelper> tenantDataSourceHelperProvider;

    @Around("execution(* com.lambda.fusion.ai.service..*.*(..))")
    public Object aroundServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        TenantDataSourceHelper tenantDataSourceHelper = tenantDataSourceHelperProvider.getIfAvailable();
        if (tenantDataSourceHelper == null) {
            return joinPoint.proceed();
        }
        String tenantId = OperatorUtils.getSafeOperator().getTenantId();
        String targetDataSource = tenantDataSourceHelper.resolveTargetDataSourceName(tenantId);

        log.debug(
                "AI Service 数据源切换: 方法={}, 租户ID={}, 目标数据源={}",
                joinPoint.getSignature().toShortString(),
                tenantId,
                targetDataSource);

        try (DataSourceSwitcher ignored = DataSourceSwitcher.switchTo(targetDataSource)) {
            return joinPoint.proceed();
        }
    }
}
