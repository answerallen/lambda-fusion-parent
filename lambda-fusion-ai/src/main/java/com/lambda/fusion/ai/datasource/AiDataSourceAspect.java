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
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AiDataSourceAspect {

    private final AiProperties aiProperties;

    @Around("execution(* com.lambda.fusion.ai..*.service..*.*(..))")
    public Object aroundServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!Boolean.TRUE.equals(aiProperties.getDataSource().getEnabled())) {
            return joinPoint.proceed();
        }
        try (DataSourceSwitcher ignored =
                DataSourceSwitcher.switchTo(aiProperties.getDataSource().getName())) {
            return joinPoint.proceed();
        }
    }
}
