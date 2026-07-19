package com.lambda.fusion.ai.agent.runtime;

import com.lambda.cloud.datasource.dynamic.DynamicDataSourceService;
import com.lambda.fusion.ai.AiProperties;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 2.0 运行时装配。
 *
 * <p>条件装配（遵循 AGENTS.md「三件套」约定）：仅当 classpath 存在 {@link PostgresAgentStateStore}
 * 且 {@code lambda.fusion.ai.agentscope.enabled=true}（默认开）时生效。装配分布式会话后端
 * {@link AgentStateStore}（PG 经 {@code agentscope-extensions-postgresql} 一等接入，spike 已核实），
 * 复用 AI 模块现有 ai-postgres {@link DataSource}（与 AI 业务表同库，见方案 §5.5 / D5）。
 *
 * <p>{@link AgentRuntimeServiceImpl} 经 {@code ObjectProvider<AgentStateStore>} 注入；若后端未装配
 * （DS 不可用或 backend 非 pg），HarnessAgent 回落 JsonFile 默认，运行时不中断。
 *
 * @author Jin
 */
@Slf4j
@Configuration
@ConditionalOnClass(PostgresAgentStateStore.class)
@ConditionalOnProperty(
        prefix = "lambda.fusion.ai.agentscope",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
public class AgentScopeRuntimeConfiguration {

    private final AiProperties aiProperties;
    private final AgentScopeRuntimeProperties runtimeProperties;
    private final DynamicDataSourceService dynamicDataSourceService;

    /**
     * PG 会话状态后端（默认；backend=pg 时装配）。
     *
     * <p>复用 ai-postgres {@link DataSource}；DS 不可用时跳过装配（返回 null，ObjectProvider 视为缺失，
     * HarnessAgent 回落 JsonFile），不阻断启动。
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "lambda.fusion.ai.agentscope.session",
            name = "backend",
            havingValue = "pg",
            matchIfMissing = true)
    public AgentStateStore agentStateStore() {
        String dsName = aiProperties.getDataSource().getName();
        DataSource dataSource;
        try {
            dataSource = dynamicDataSourceService.getDataSource(dsName);
        } catch (Exception e) {
            log.warn("AgentScope AgentStateStore: 获取数据源 '{}' 失败，跳过 PG 会话后端装配: {}", dsName, e.getMessage());
            return null;
        }
        if (dataSource == null) {
            log.warn("AgentScope AgentStateStore: 数据源 '{}' 为 null，跳过 PG 会话后端（HarnessAgent 回落 JsonFile 默认）", dsName);
            return null;
        }
        AgentScopeRuntimeProperties.Session cfg = runtimeProperties.getSession();
        log.info(
                "AgentScope AgentStateStore: PG 后端, ds={}, schema={}, table={}, createIfNotExist={}",
                dsName,
                cfg.getSchemaName(),
                cfg.getTableName(),
                cfg.isCreateIfNotExist());
        return PostgresAgentStateStore.builder(dataSource)
                .schemaName(cfg.getSchemaName())
                .tableName(cfg.getTableName())
                .createIfNotExist(cfg.isCreateIfNotExist())
                .build();
    }
}
