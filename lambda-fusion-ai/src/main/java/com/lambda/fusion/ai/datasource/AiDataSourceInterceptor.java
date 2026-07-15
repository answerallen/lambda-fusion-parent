package com.lambda.fusion.ai.datasource;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.datasource.api.DataSourceSwitcher;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * AI 数据源 SQL 级拦截器（逃逸安全网）
 *
 * <p>在 MyBatis {@link Executor} 执行 SQL 前后切换到 AI 数据源，覆盖 {@link AiDataSourceAspect}
 * 抓不到的路径：非事务的直接 mapper 调用、虚拟线程 / ForkJoinPool / 线程池 / 自调用等。
 * 在「执行 SQL 的线程」上 push 数据源，与调用方位置和调用栈无关。
 *
 * <p>仅对 AI mapper（{@link MappedStatement#getId()} 以 {@code com.lambda.fusion.ai.} 开头）生效，
 * 业务 mapper 透传不受影响。事务路径（声明式 {@code @Transactional} 或编程式
 * {@code TransactionTemplate}）的连接在事务 begin 时即获取，早于本拦截器，仍由
 * {@link AiDataSourceAspect} 在事务开始前负责。
 *
 * <p>{@link AiProperties.AiDataSource#getEnabled()} 为 false 时整体 no-op。
 */
@Intercepts({
    @Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
            type = Executor.class,
            method = "query",
            args = {
                MappedStatement.class,
                Object.class,
                RowBounds.class,
                ResultHandler.class,
                CacheKey.class,
                BoundSql.class
            }),
    @Signature(
            type = Executor.class,
            method = "update",
            args = {MappedStatement.class, Object.class}),
    @Signature(
            type = Executor.class,
            method = "queryCursor",
            args = {MappedStatement.class, Object.class, RowBounds.class})
})
public class AiDataSourceInterceptor implements Interceptor {

    private static final String AI_MAPPER_PREFIX = "com.lambda.fusion.ai.";

    private final AiProperties aiProperties;

    public AiDataSourceInterceptor(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!Boolean.TRUE.equals(aiProperties.getDataSource().getEnabled())) {
            return invocation.proceed();
        }
        Object mappedStatementArg = invocation.getArgs()[0];
        if (!(mappedStatementArg instanceof MappedStatement mappedStatement)) {
            return invocation.proceed();
        }
        if (!mappedStatement.getId().startsWith(AI_MAPPER_PREFIX)) {
            return invocation.proceed();
        }
        try (DataSourceSwitcher ignored =
                DataSourceSwitcher.switchTo(aiProperties.getDataSource().getName())) {
            return invocation.proceed();
        }
    }
}
