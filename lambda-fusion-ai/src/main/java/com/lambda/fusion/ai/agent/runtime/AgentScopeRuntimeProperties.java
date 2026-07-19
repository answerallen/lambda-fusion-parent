package com.lambda.fusion.ai.agent.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentScope 2.0 运行时配置。
 *
 * <p>前缀 {@code lambda.fusion.ai.agentscope}。控制 AgentScope 运行时的可调参数：默认迭代上限、
 * 分布式会话后端（PG/Redis）选择与表定位。模型/工具/MCP 等资源仍由各自 DB 驱动的管理面提供。
 *
 * <p>参见 {@code docs/refactor/ai-agentscope-spike.md} 与 {@code docs/refactor/ai-agentscope-refactor.md} §5.5 / D5。
 *
 * @author Jin
 */
@Data
@ConfigurationProperties(prefix = "lambda.fusion.ai.agentscope")
public class AgentScopeRuntimeProperties {

    /** 是否启用 AgentScope 运行时（关闭则不装配 AgentRuntimeService 等 Bean）。 */
    private boolean enabled = true;

    /** 默认最大推理-行动循环迭代次数（防失控）。 */
    private int defaultMaxIters = 20;

    /** 模型调用超时（秒）。 */
    private int modelTimeoutSeconds = 60;

    /** 模型调用重试次数（含首次；AgentScope ExecutionConfig 原生重试+指数退避，取代旧 Resilience4j Retry）。 */
    private int modelMaxAttempts = 3;

    /** 分布式会话/记忆后端配置。 */
    private Session session = new Session();

    @Data
    public static class Session {

        /** 会话后端类型：{@code pg}（PostgresAgentStateStore，复用 ai-postgres）或 {@code redis}。 */
        private String backend = "pg";

        /** PG schema 名（仅 backend=pg 生效）。 */
        private String schemaName = "public";

        /** PG 会话状态表名（仅 backend=pg 生效，表由扩展按 createIfNotExist 自动建）。 */
        private String tableName = "ai_agent_state";

        /** 是否在表不存在时自动创建（仅 backend=pg 生效）。 */
        private boolean createIfNotExist = true;
    }
}
