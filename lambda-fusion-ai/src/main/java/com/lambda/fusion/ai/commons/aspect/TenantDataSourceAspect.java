package com.lambda.fusion.ai.commons.aspect;

import cn.hutool.core.util.StrUtil;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.commons.datasource.TenantDataSourceHelper;
import com.lambda.fusion.core.utils.AuthUtils;
import com.lambda.fusion.datasource.commons.api.DataSourceSwitcher;
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

    private final TenantDataSourceHelper tenantDataSourceHelper;
    private final AiProperties aiProperties;

    @Around("execution(* com.lambda.fusion.ai.service..*.*(..))")
    public Object aroundServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String tenantId = AuthUtils.getTenantId();
        String targetDataSource = resolveTargetDataSource(tenantId);

        log.debug(
                "AI Service 数据源切换: 方法={}, 租户ID={}, 目标数据源={}",
                joinPoint.getSignature().toShortString(),
                tenantId,
                targetDataSource);

        try (DataSourceSwitcher ignored = DataSourceSwitcher.switchTo(targetDataSource)) {
            return joinPoint.proceed();
        }
    }


    private String resolveTargetDataSource(String tenantId) {
        if (StrUtil.isNotBlank(tenantId) && !"default".equals(tenantId)) {
            String tenantDsName = tenantDataSourceHelper.getTenantDataSourceName(tenantId);
            if (tenantDataSourceHelper.tenantDataSourceExists(tenantId)) {
                return tenantDsName;
            }
            log.debug("租户数据源不存在，使用默认数据源: tenantId={}, dataSource={}", tenantId, tenantDsName);
        }
        return aiProperties.getDataSource().getName();
    }
}
