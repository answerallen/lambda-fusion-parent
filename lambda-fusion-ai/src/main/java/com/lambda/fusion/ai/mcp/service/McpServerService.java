package com.lambda.fusion.ai.mcp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.mcp.model.CreateMcpServer;
import com.lambda.fusion.ai.mcp.model.McpServerPage;
import com.lambda.fusion.ai.mcp.model.UpdateMcpServer;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import io.agentscope.core.tool.mcp.McpClientWrapper;

public interface McpServerService {

    Page<McpServerEntity> page(McpServerPage query);

    McpServerEntity get(String id);

    McpServerEntity create(CreateMcpServer dto);

    void update(String id, UpdateMcpServer dto);

    void delete(String id);

    McpServerEntity loadById(String id);

    /**
     * 按实体配置构建 MCP 客户端包装器。同步阻塞调用，stdio 拉起子进程，SSE/HTTP 发起网络请求。
     * 调用方使用完毕必须 {@link McpClientWrapper#close()} 释放资源。
     *
     * @throws AiBusinessException MCP_TRANSPORT_NOT_SUPPORTED 传输类型不合法时
     */
    McpClientWrapper buildWrapper(McpServerEntity entity);

    /**
     * 测试 MCP 服务连通性，返回发现的工具数量。
     */
    int testConnection(String id);
}
