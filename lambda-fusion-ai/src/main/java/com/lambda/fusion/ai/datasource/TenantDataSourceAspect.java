package com.lambda.fusion.ai.datasource;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.datasource.api.DataSourceSwitcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AI Service 数据源切面
 * <p>
 * 字段级租户隔离模型下，所有 AI Service 层方法统一路由到共享的 AI 数据源（默认
 * {@code ai-postgres}）；租户隔离由 {@code tenant_id} 字段过滤完成，不再按租户切换
 * 独立数据源。
 * </p>
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class TenantDataSourceAspect {

    private final AiProperties aiProperties;

    @Around("execution(* com.lambda.fusion.ai..*.service..*.*(..))")
    public Object aroundServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        try (DataSourceSwitcher ignored =
                DataSourceSwitcher.switchTo(aiProperties.getDataSource().getName())) {
            return joinPoint.proceed();
        }
    }
}
