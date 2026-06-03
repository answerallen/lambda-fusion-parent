package com.lambda.fusion.ai.support.mcp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.McpServerMapper;
import com.lambda.fusion.ai.model.entity.McpServerEntity;
import com.lambda.fusion.ai.support.factory.ChatModelFactory;
import com.lambda.fusion.datasource.commons.api.DataSourceSwitcher;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
 * MCP（Model Context Protocol）客户端管理器
 * <p>
 * 负责根据数据库配置创建、缓存和生命周期管理 {@link McpClient} 实例。
 * 与 {@link ChatModelFactory} 的设计思路一致：
 * 按需创建客户端（基于 McpServerId），并通过 Caffeine 缓存 30 分钟，
 * 超时或配置变更时自动关闭旧连接。
 * <p>
 * 支持的传输类型：
 * <ul>
 *   <li>STDIO：通过子进程协议与本地 MCP Server 通信</li>
 *   <li>HTTP_STREAMABLE：通过 Streamable HTTP 与远程 MCP Server 通信</li>
 * </ul>
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

    public static final String TRANSPORT_STDIO = "STDIO";
    public static final String TRANSPORT_HTTP_STREAMABLE = "HTTP_STREAMABLE";

    private final McpServerMapper mcpServerMapper;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    /**
     * McpClient 实例缓存（key = McpServer.id）
     * 超时自动关闭旧连接，防止资源泄漏
     */
    private final Cache<String, McpClient> clientCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(50)
            .removalListener((key, client, cause) -> {
                if (client instanceof McpClient mcpClient) {
                    closeSilently(mcpClient, String.valueOf(key));
                }
            })
            .build();

    /**
     * 根据 McpServerId 获取 McpClient（含本地缓存）
     *
     * @param mcpServerId MCP 服务器ID
     * @return McpClient 实例
     * @throws AiBusinessException 若服务器不存在或已禁用
     */
    public McpClient getClient(String mcpServerId) {
        return clientCache.get(mcpServerId, id -> {
            try (DataSourceSwitcher ignored =
                    DataSourceSwitcher.switchTo(aiProperties.getDataSource().getName())) {
                McpServerEntity entity = mcpServerMapper.selectById(id);
                if (entity == null) {
                    throw new AiBusinessException(AiErrorCode.MCP_SERVER_NOT_FOUND, "MCP服务器不存在: " + id);
                }
                if (!Boolean.TRUE.equals(entity.getEnabled())) {
                    throw new AiBusinessException(AiErrorCode.MCP_SERVER_DISABLED, "MCP服务器已禁用: " + id);
                }
                log.info("McpClientManager: 创建新 McpClient，服务器: {} ({})", entity.getName(), entity.getTransportType());
                return buildClient(entity);
            }
        });
    }

    /**
     * 获取所有启用的 McpClient 列表
     * <p>
     * 用于 AgentToolProvider 在初始化时加载所有可用的 MCP 工具规格。
     *
     * @return 所有已启用并成功连接的 McpClient 列表（若某个服务器连接失败则跳过）
     */
    public List<McpClient> getAllEnabledClients() {
        try (DataSourceSwitcher ignored =
                DataSourceSwitcher.switchTo(aiProperties.getDataSource().getName())) {
            List<McpServerEntity> servers = mcpServerMapper.selectEnabled();
            if (servers == null || servers.isEmpty()) {
                return Collections.emptyList();
            }
            List<McpClient> clients = new ArrayList<>();
            for (McpServerEntity server : servers) {
                try {
                    McpClient client = getClient(server.getId());
                    client.checkHealth();
                    clients.add(client);
                } catch (Exception e) {
                    invalidateCache(server.getId());
                    log.warn("McpClientManager: 加载 MCP 服务器 '{}' 失败，已跳过: {}", server.getName(), e.getMessage());
                }
            }
            log.info("McpClientManager: 共加载 {}/{} 个 MCP 服务器客户端", clients.size(), servers.size());
            return clients;
        }
    }

    /**
     * 使指定 McpServer 的缓存客户端失效（配置变更时调用）
     *
     * @param mcpServerId MCP 服务器ID
     */
    public void invalidateCache(String mcpServerId) {
        clientCache.invalidate(mcpServerId);
        log.info("McpClientManager: 已清除 MCP 客户端缓存，服务器ID: {}", mcpServerId);
    }

    /**
     * 使所有缓存失效（系统维护时调用）
     */
    public void invalidateAll() {
        clientCache.invalidateAll();
        log.info("McpClientManager: 已清除所有 MCP 客户端缓存");
    }

    /**
     * 检查指定 MCP 服务器是否可达且能正常响应健康检查。
     *
     * @param mcpServerId MCP 服务器ID
     */
    public void checkConnection(String mcpServerId) {
        invalidateCache(mcpServerId);
        try {
            McpClient client = getClient(mcpServerId);
            client.checkHealth();
        } catch (Exception e) {
            invalidateCache(mcpServerId);
            throw e;
        }
    }

    // ========================== 私有方法 ==========================

    private McpClient buildClient(McpServerEntity entity) {
        int timeout = entity.getTimeoutSeconds() != null ? entity.getTimeoutSeconds() : 30;
        McpTransport transport = buildTransport(entity);
        return DefaultMcpClient.builder()
                .key(entity.getId())
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(timeout))
                .build();
    }

    private McpTransport buildTransport(McpServerEntity entity) {
        String type = entity.getTransportType();
        if (!StringUtils.hasText(type)) {
            throw new AiBusinessException(AiErrorCode.MCP_TRANSPORT_NOT_SUPPORTED, "MCP传输类型未配置，服务器: " + entity.getId());
        }

        return switch (type.toUpperCase()) {
            case TRANSPORT_STDIO -> buildStdioTransport(entity);
            case TRANSPORT_HTTP_STREAMABLE -> buildHttpStreamableTransport(entity);
            default ->
                throw new AiBusinessException(
                        AiErrorCode.MCP_TRANSPORT_NOT_SUPPORTED, "不支持的MCP传输类型: " + type + "，服务器: " + entity.getId());
        };
    }

    private McpTransport buildStdioTransport(McpServerEntity entity) {
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

            StdioMcpTransport.Builder builder =
                    StdioMcpTransport.builder().command(command).logEvents(log.isDebugEnabled());

            // 解析环境变量
            if (StringUtils.hasText(entity.getEnvVars())) {
                Map<String, String> env = objectMapper.readValue(entity.getEnvVars(), new TypeReference<>() {});
                if (env != null && !env.isEmpty()) {
                    builder.environment(env);
                }
            }

            return builder.build();
        } catch (AiBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new AiBusinessException(
                    AiErrorCode.MCP_SERVER_CONNECTION_FAILED,
                    "解析 STDIO command 失败（需要合法 JSON 数组格式），服务器: " + entity.getId() + "，原因: " + e.getMessage());
        }
    }

    private McpTransport buildHttpStreamableTransport(McpServerEntity entity) {
        if (!StringUtils.hasText(entity.getUrl())) {
            throw new AiBusinessException(
                    AiErrorCode.MCP_SERVER_CONNECTION_FAILED, "HTTP_STREAMABLE 传输类型需要配置 url 字段，服务器: " + entity.getId());
        }
        return StreamableHttpMcpTransport.builder()
                .url(entity.getUrl())
                .logRequests(log.isDebugEnabled())
                .logResponses(log.isDebugEnabled())
                .build();
    }

    private void closeSilently(McpClient client, String serverId) {
        try {
            if (client instanceof AutoCloseable closeable) {
                closeable.close();
                log.debug("McpClientManager: 已关闭 MCP 客户端，服务器ID: {}", serverId);
            }
        } catch (Exception e) {
            log.warn("McpClientManager: 关闭 MCP 客户端失败，服务器ID: {}，原因: {}", serverId, e.getMessage());
        }
    }
}
