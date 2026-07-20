package com.lambda.fusion.ai.agent.runtime;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mcp.mapper.McpServerMapper;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * AgentScope MCP 客户端适配器：从 {@link McpServerEntity} 构造 AgentScope {@code McpClientWrapper}。
 *
 * <p>从 {@link McpServerEntity}（{@code ai_mcp_server}）按 serverId 构造 AgentScope
 * {@link McpClientWrapper}（经 {@link McpClientBuilder}），Caffeine 缓存 + 自动关闭。
 * 传输映射：{@code STDIO} -> {@code stdioTransport(cmd, args, env)}、{@code HTTP_STREAMABLE} ->
 * {@code streamableHttpTransport(url)}。底层用官方 MCP SDK（{@code io.modelcontextprotocol}）。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientAdapter {

    public static final String TRANSPORT_STDIO = "STDIO";
    public static final String TRANSPORT_HTTP_STREAMABLE = "HTTP_STREAMABLE";

    private final McpServerMapper mcpServerMapper;
    private final ObjectMapper objectMapper;

    // McpClientWrapper 实例缓存（key = McpServer.id），超时自动关闭旧连接
    private final Cache<String, McpClientWrapper> clientCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(50)
            .<String, McpClientWrapper>removalListener(
                    (key, client, cause) -> closeSilently(client, String.valueOf(key)))
            .build();

    /**
     * 按 serverId 获取 AgentScope MCP 客户端（含缓存）。
     *
     * @param mcpServerId MCP 服务器ID
     * @return {@link McpClientWrapper} 实例
     * @throws AiBusinessException 服务器不存在/已禁用/传输配置非法
     */
    public McpClientWrapper get(String mcpServerId) {
        return clientCache.get(mcpServerId, id -> {
            McpServerEntity entity = mcpServerMapper.selectById(id);
            if (entity == null) {
                throw new AiBusinessException(AiErrorCode.MCP_SERVER_NOT_FOUND, "MCP服务器不存在: " + id);
            }
            if (!Boolean.TRUE.equals(entity.getEnabled())) {
                throw new AiBusinessException(AiErrorCode.MCP_SERVER_DISABLED, "MCP服务器已禁用: " + id);
            }
            log.info(
                    "McpClientAdapter: 创建新 McpClientWrapper，服务器: {} ({})", entity.getName(), entity.getTransportType());
            return buildClient(entity);
        });
    }

    public void invalidateCache(String mcpServerId) {
        clientCache.invalidate(mcpServerId);
        log.info("McpClientAdapter: 已清除 MCP 客户端缓存，服务器ID: {}", mcpServerId);
    }

    public void invalidateAll() {
        clientCache.invalidateAll();
        log.info("McpClientAdapter: 已清除所有 MCP 客户端缓存");
    }

    /**
     * 检查指定 MCP 服务器可达：失效缓存后重建并完成 MCP 握手 + 列工具验证（同步阻塞，供管理面 testConnection）。
     *
     * @param mcpServerId MCP 服务器ID
     */
    public void checkConnection(String mcpServerId) {
        invalidateCache(mcpServerId);
        McpClientWrapper client = get(mcpServerId);
        client.initialize().block();
        client.listTools().block();
    }

    // ==================== 私有 ====================

    private McpClientWrapper buildClient(McpServerEntity entity) {
        int timeout = entity.getTimeoutSeconds() != null ? entity.getTimeoutSeconds() : 30;
        McpClientBuilder builder = McpClientBuilder.create(entity.getName())
                .timeout(Duration.ofSeconds(timeout))
                .initializationTimeout(Duration.ofSeconds(timeout));

        String type = entity.getTransportType();
        if (!StringUtils.hasText(type)) {
            throw new AiBusinessException(AiErrorCode.MCP_TRANSPORT_NOT_SUPPORTED, "MCP传输类型未配置，服务器: " + entity.getId());
        }
        switch (type.toUpperCase()) {
            case TRANSPORT_STDIO -> configureStdio(builder, entity);
            case TRANSPORT_HTTP_STREAMABLE -> {
                if (!StringUtils.hasText(entity.getUrl())) {
                    throw new AiBusinessException(
                            AiErrorCode.MCP_SERVER_CONNECTION_FAILED,
                            "HTTP_STREAMABLE 传输类型需要配置 url 字段，服务器: " + entity.getId());
                }
                builder.streamableHttpTransport(entity.getUrl());
            }
            default ->
                throw new AiBusinessException(
                        AiErrorCode.MCP_TRANSPORT_NOT_SUPPORTED, "不支持的MCP传输类型: " + type + "，服务器: " + entity.getId());
        }
        return builder.buildSync();
    }

    private void configureStdio(McpClientBuilder builder, McpServerEntity entity) {
        if (!StringUtils.hasText(entity.getCommand())) {
            throw new AiBusinessException(
                    AiErrorCode.MCP_SERVER_CONNECTION_FAILED, "STDIO 传输类型需要配置 command 字段，服务器: " + entity.getId());
        }
        try {
            List<String> command = objectMapper.readValue(entity.getCommand(), new TypeReference<>() {});
            if (command == null || command.isEmpty()) {
                throw new AiBusinessException(
                        AiErrorCode.MCP_SERVER_CONNECTION_FAILED, "STDIO command 不能为空，服务器: " + entity.getId());
            }
            String cmd = command.get(0);
            List<String> args = command.size() > 1 ? command.subList(1, command.size()) : List.of();
            Map<String, String> env = new HashMap<>();
            if (StringUtils.hasText(entity.getEnvVars())) {
                Map<String, String> parsed = objectMapper.readValue(entity.getEnvVars(), new TypeReference<>() {});
                if (parsed != null && !parsed.isEmpty()) {
                    env.putAll(parsed);
                }
            }
            builder.stdioTransport(cmd, args, env);
        } catch (AiBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new AiBusinessException(
                    AiErrorCode.MCP_SERVER_CONNECTION_FAILED,
                    "解析 STDIO command 失败（需要合法 JSON 数组格式），服务器: " + entity.getId() + "，原因: " + e.getMessage());
        }
    }

    private static void closeSilently(McpClientWrapper client, String serverId) {
        if (client == null) {
            return;
        }
        try {
            client.close();
            log.debug("McpClientAdapter: 已关闭 MCP 客户端，服务器ID: {}", serverId);
        } catch (Exception e) {
            log.warn("McpClientAdapter: 关闭 MCP 客户端失败，服务器ID: {}，原因: {}", serverId, e.getMessage());
        }
    }
}
