package com.lambda.fusion.ai.mcp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.mcp.model.CreateMcpServer;
import com.lambda.fusion.ai.mcp.model.McpServerPage;
import com.lambda.fusion.ai.mcp.model.UpdateMcpServer;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import io.agentscope.core.tool.mcp.McpClientWrapper;

/**
 * MCP 服务管理。
 *
 * @author Jin
 */
public interface McpServerService {

    Page<McpServerEntity> page(McpServerPage query);

    McpServerEntity get(String id);

    McpServerEntity create(CreateMcpServer dto);

    void update(String id, UpdateMcpServer dto);

    void delete(String id);

    McpServerEntity loadById(String id);

    McpClientWrapper buildWrapper(McpServerEntity entity);

    /**
     * 测试 MCP 服务连通性，返回发现的工具数量。
     */
    int testConnection(String id);
}
