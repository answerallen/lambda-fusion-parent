package com.lambda.fusion.ai.mcp.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mcp.mapper.McpServerMapper;
import com.lambda.fusion.ai.mcp.model.CreateMcpServer;
import com.lambda.fusion.ai.mcp.model.McpServerPage;
import com.lambda.fusion.ai.mcp.model.UpdateMcpServer;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import com.lambda.fusion.ai.mcp.service.McpServerService;
import com.lambda.fusion.ai.runtime.event.ConfigChangedEvent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class McpServerServiceImpl implements McpServerService {

    private static final String TRANSPORT_STDIO = "stdio";
    private static final String TRANSPORT_SSE = "sse";
    private static final String TRANSPORT_HTTP = "http";
    private static final String TRANSPORT_STREAMABLE_HTTP = "streamable_http";

    private final McpServerMapper mcpServerMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Page<McpServerEntity> page(McpServerPage query) {
        return mcpServerMapper.selectPage(query.getPage(), query.getLambdaQueryWrapper());
    }

    @Override
    public McpServerEntity get(String id) {
        return requireExists(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServerEntity create(CreateMcpServer dto) {
        validateTransport(dto.getTransport());
        ensureNameUnique(dto.getName(), null);
        McpServerEntity entity = new McpServerEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setName(dto.getName());
        entity.setTransport(dto.getTransport());
        entity.setCommand(dto.getCommand());
        entity.setArgs(dto.getArgs());
        entity.setEnv(dto.getEnv());
        entity.setUrl(dto.getUrl());
        entity.setHeaders(dto.getHeaders());
        entity.setEnableTools(dto.getEnableTools());
        entity.setTimeout(dto.getTimeout());
        entity.setEnabled(dto.getEnabled());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        mcpServerMapper.insert(entity);
        eventPublisher.publishEvent(ConfigChangedEvent.all());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpdateMcpServer dto) {
        McpServerEntity entity = requireExists(id);
        if (StringUtils.isNotBlank(dto.getTransport())) {
            validateTransport(dto.getTransport());
            entity.setTransport(dto.getTransport());
        }
        if (StringUtils.isNotBlank(dto.getName()) && !dto.getName().equals(entity.getName())) {
            ensureNameUnique(dto.getName(), id);
            entity.setName(dto.getName());
        }
        if (dto.getCommand() != null) {
            entity.setCommand(dto.getCommand());
        }
        if (dto.getArgs() != null) {
            entity.setArgs(dto.getArgs());
        }
        if (dto.getEnv() != null) {
            entity.setEnv(dto.getEnv());
        }
        if (dto.getUrl() != null) {
            entity.setUrl(dto.getUrl());
        }
        if (dto.getHeaders() != null) {
            entity.setHeaders(dto.getHeaders());
        }
        if (dto.getEnableTools() != null) {
            entity.setEnableTools(dto.getEnableTools());
        }
        if (dto.getTimeout() != null) {
            entity.setTimeout(dto.getTimeout());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        mcpServerMapper.updateById(entity);
        eventPublisher.publishEvent(ConfigChangedEvent.all());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        requireExists(id);
        mcpServerMapper.deleteById(id);
        eventPublisher.publishEvent(ConfigChangedEvent.all());
    }

    @Override
    public McpServerEntity loadById(String id) {
        return requireExists(id);
    }

    @Override
    public McpClientWrapper buildWrapper(McpServerEntity entity) {
        // buildSync() 同步阻塞：stdio 拉起子进程，SSE/HTTP 发起网络连接
        String transport = entity.getTransport();
        McpClientBuilder builder = McpClientBuilder.create(entity.getName());
        switch (transport) {
            case TRANSPORT_STDIO:
                builder.stdioTransport(
                        entity.getCommand(),
                        entity.getArgs() != null ? entity.getArgs() : List.of(),
                        entity.getEnv() != null ? entity.getEnv() : Map.of());
                break;
            case TRANSPORT_SSE:
                builder.sseTransport(entity.getUrl());
                if (entity.getHeaders() != null) {
                    builder.headers(entity.getHeaders());
                }
                break;
            case TRANSPORT_HTTP:
            case TRANSPORT_STREAMABLE_HTTP:
                builder.streamableHttpTransport(entity.getUrl());
                if (entity.getHeaders() != null) {
                    builder.headers(entity.getHeaders());
                }
                break;
            default:
                throw new AiBusinessException(AiErrorCode.MCP_TRANSPORT_NOT_SUPPORTED, transport);
        }
        if (entity.getTimeout() != null) {
            builder.timeout(Duration.ofMillis(entity.getTimeout()));
        }
        return builder.buildSync();
    }

    @Override
    public int testConnection(String id) {
        McpServerEntity entity = requireExists(id);
        McpClientWrapper wrapper = buildWrapper(entity);
        try {
            var tools = wrapper.listTools().block();
            return tools == null ? 0 : tools.size();
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.MCP_SERVER_CONNECTION_FAILED, e);
        } finally {
            try {
                wrapper.close();
            } catch (Exception ignored) {
            }
        }
    }

    private McpServerEntity requireExists(String id) {
        McpServerEntity entity =
                mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServerEntity>().eq(McpServerEntity::getId, id));
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.MCP_SERVER_NOT_FOUND, id);
        }
        return entity;
    }

    private void ensureNameUnique(String name, String excludeId) {
        boolean exists = mcpServerMapper.exists(new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getName, name)
                .ne(excludeId != null, McpServerEntity::getId, excludeId));
        if (exists) {
            throw new AiBusinessException(AiErrorCode.MCP_SERVER_NAME_EXISTS, name);
        }
    }

    private void validateTransport(String transport) {
        if (!TRANSPORT_STDIO.equals(transport)
                && !TRANSPORT_SSE.equals(transport)
                && !TRANSPORT_HTTP.equals(transport)
                && !TRANSPORT_STREAMABLE_HTTP.equals(transport)) {
            throw new AiBusinessException(AiErrorCode.MCP_TRANSPORT_NOT_SUPPORTED, transport);
        }
    }
}
