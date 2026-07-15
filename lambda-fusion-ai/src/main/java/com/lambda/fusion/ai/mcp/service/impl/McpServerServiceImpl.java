package com.lambda.fusion.ai.mcp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mcp.manager.McpClientManager;
import com.lambda.fusion.ai.mcp.mapper.McpServerMapper;
import com.lambda.fusion.ai.mcp.model.CreateMcpServer;
import com.lambda.fusion.ai.mcp.model.McpServer;
import com.lambda.fusion.ai.mcp.model.UpdateMcpServer;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import com.lambda.fusion.ai.mcp.service.McpServerService;
import com.lambda.fusion.core.utils.AuthUtils;
import com.lambda.fusion.datasource.api.DataSourceSwitcher;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * MCP Server 管理服务实现
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl extends ServiceImpl<McpServerMapper, McpServerEntity> implements McpServerService {

    private final McpServerMapper mcpServerMapper;
    private final McpClientManager mcpClientManager;
    private final AgentToolProvider agentToolProvider;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(CreateMcpServer request) {
        String currentTenantId = AuthUtils.getTenantId();
        // 检查名称唯一性
        McpServerEntity existing = mcpServerMapper.selectByName(request.getName());
        if (existing != null && sameTenant(existing.getTenantId(), currentTenantId)) {
            throw new AiBusinessException(AiErrorCode.MCP_SERVER_NAME_EXISTS, "MCP服务器名称已存在: " + request.getName());
        }
        // 参数校验
        validateTransportParams(request.getTransportType(), request.getCommand(), request.getUrl());

        McpServerEntity entity = buildEntity(request);
        applyDefaults(entity);
        if (!StringUtils.hasText(entity.getTenantId())) {
            entity.setTenantId(currentTenantId);
        }
        mcpServerMapper.insert(entity);

        // 配置变更后刷新工具列表
        refreshToolsAfterCommit(entity.getId());

        log.info("McpServerServiceImpl: 创建 MCP 服务器成功，ID: {}，名称: {}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, UpdateMcpServer request) {
        McpServerEntity entity = getOrThrow(id);

        // 如果有修改传输类型，校验对应字段
        String newTransportType = StrUtil.emptyToDefault(request.getTransportType(), entity.getTransportType());
        String newCommand = StrUtil.emptyToDefault(request.getCommand(), entity.getCommand());
        String newUrl = StrUtil.emptyToDefault(request.getUrl(), entity.getUrl());
        validateTransportParams(newTransportType, newCommand, newUrl);

        // 更新字段
        if (StringUtils.hasText(request.getDisplayName())) {
            entity.setDisplayName(request.getDisplayName());
        }
        if (StringUtils.hasText(request.getDescription())) {
            entity.setDescription(request.getDescription());
        }
        if (StringUtils.hasText(request.getTransportType())) {
            entity.setTransportType(request.getTransportType());
        }
        if (StringUtils.hasText(request.getCommand())) {
            entity.setCommand(request.getCommand());
        }
        if (StringUtils.hasText(request.getUrl())) {
            entity.setUrl(request.getUrl());
        }
        if (StringUtils.hasText(request.getEnvVars())) {
            entity.setEnvVars(request.getEnvVars());
        }
        if (request.getTimeoutSeconds() != null) {
            entity.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }

        applyDefaults(entity);
        mcpServerMapper.updateById(entity);

        // 使缓存失效并刷新工具
        refreshToolsAfterCommit(id);

        log.info("McpServerServiceImpl: 更新 MCP 服务器成功，ID: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        getOrThrow(id);
        mcpServerMapper.deleteById(id);

        // 使缓存失效并刷新工具
        refreshToolsAfterCommit(id);

        log.info("McpServerServiceImpl: 删除 MCP 服务器成功，ID: {}", id);
    }

    @Override
    public McpServer getServerById(String id) {
        return toVo(getOrThrow(id));
    }

    @Override
    public List<McpServer> listAll() {
        List<McpServerEntity> entities = mcpServerMapper.selectByTenantId(AuthUtils.getTenantId());
        return entities.stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public boolean testConnection(String id) {
        getOrThrow(id);
        try {
            mcpClientManager.checkConnection(id);
            log.info("McpServerServiceImpl: MCP 服务器连接测试成功，ID: {}", id);
            return true;
        } catch (Exception e) {
            log.warn("McpServerServiceImpl: MCP 服务器连接测试失败，ID: {}，原因: {}", id, e.getMessage());
            return false;
        }
    }

    // ========================== 私有辅助方法 ==========================

    private McpServerEntity getOrThrow(String id) {
        McpServerEntity entity = mcpServerMapper.selectById(id);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.MCP_SERVER_NOT_FOUND, "MCP服务器不存在: " + id);
        }
        validateTenantAccess(entity);
        return entity;
    }

    private McpServerEntity buildEntity(CreateMcpServer request) {
        return request.toEntity();
    }

    private McpServer toVo(McpServerEntity entity) {
        return McpServer.fromEntity(McpServer.class, entity);
    }

    private void validateTransportParams(String transportType, String command, String url) {
        if (!StringUtils.hasText(transportType)) {
            return;
        }
        switch (transportType.toUpperCase()) {
            case McpClientManager.TRANSPORT_STDIO -> {
                if (!StringUtils.hasText(command)) {
                    throw new AiBusinessException(
                            AiErrorCode.INVALID_PARAMETER,
                            "STDIO 传输类型需要配置 command 字段（JSON数组格式，如 [\"node\", \"server.js\"]）");
                }
                validateJsonArray(command, "command");
            }
            case McpClientManager.TRANSPORT_HTTP_STREAMABLE -> {
                if (!StringUtils.hasText(url)) {
                    throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "HTTP_STREAMABLE 传输类型需要配置 url 字段");
                }
            }
            default ->
                throw new AiBusinessException(AiErrorCode.MCP_TRANSPORT_NOT_SUPPORTED, "不支持的传输类型: " + transportType);
        }
        if (StringUtils.hasText(command) && McpClientManager.TRANSPORT_STDIO.equalsIgnoreCase(transportType)) {
            validateJsonArray(command, "command");
        }
    }

    private void validateJsonArray(String json, String fieldName) {
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            if (parsed == null || parsed.isEmpty()) {
                throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, fieldName + " 不能为空数组");
            }
        } catch (AiBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, fieldName + " 必须是合法 JSON 数组");
        }
    }

    private void validateJsonObject(String json, String fieldName) {
        try {
            objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, fieldName + " 必须是合法 JSON 对象");
        }
    }

    /**
     * 在事务提交后失效缓存并异步刷新 MCP 工具，避免读取到未提交数据。
     */
    private void refreshToolsAfterCommit(String mcpServerId) {
        Runnable task = () -> {
            mcpClientManager.invalidateCache(mcpServerId);
            refreshToolsAsync();
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void refreshToolsAsync() {
        try {
            Thread.ofVirtual().name("mcp-refresh").start(() -> {
                try (DataSourceSwitcher ignored =
                        DataSourceSwitcher.switchTo(aiProperties.getDataSource().getName())) {
                    agentToolProvider.refreshMcpTools();
                } catch (Exception e) {
                    log.warn("McpServerServiceImpl: 异步刷新 MCP 工具列表失败: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("McpServerServiceImpl: 启动刷新线程失败: {}", e.getMessage());
        }
    }

    private void applyDefaults(McpServerEntity entity) {
        if (entity.getTimeoutSeconds() == null || entity.getTimeoutSeconds() <= 0) {
            entity.setTimeoutSeconds(30);
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        if (StringUtils.hasText(entity.getEnvVars())) {
            validateJsonObject(entity.getEnvVars(), "envVars");
        }
    }

    private void validateTenantAccess(McpServerEntity entity) {
        String currentTenantId = AuthUtils.getTenantId();
        if (!StringUtils.hasText(currentTenantId)) {
            return;
        }
        if (StringUtils.hasText(entity.getTenantId()) && !currentTenantId.equals(entity.getTenantId())) {
            throw new AiBusinessException(AiErrorCode.MCP_SERVER_NOT_FOUND, "MCP服务器不存在: " + entity.getId());
        }
    }

    private boolean sameTenant(String entityTenantId, String currentTenantId) {
        if (!StringUtils.hasText(currentTenantId)) {
            return !StringUtils.hasText(entityTenantId);
        }
        return currentTenantId.equals(entityTenantId);
    }
}
