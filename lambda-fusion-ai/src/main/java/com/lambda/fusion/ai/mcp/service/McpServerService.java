package com.lambda.fusion.ai.mcp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.mcp.model.CreateMcpServer;
import com.lambda.fusion.ai.mcp.model.McpServer;
import com.lambda.fusion.ai.mcp.model.UpdateMcpServer;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import java.util.List;

/**
 * MCP Server 管理服务接口
 *
 * @author Jin
 */
public interface McpServerService extends IService<McpServerEntity> {

    /**
     * 创建 MCP 服务器配置
     *
     * @param request 创建请求
     * @return 新创建的服务器ID
     */
    String create(CreateMcpServer request);

    /**
     * 更新 MCP 服务器配置
     *
     * @param id      服务器ID
     * @param request 更新请求
     */
    void update(String id, UpdateMcpServer request);

    /**
     * 删除 MCP 服务器配置
     *
     * @param id 服务器ID
     */
    void delete(String id);

    /**
     * 根据 ID 查询 MCP 服务器详情
     *
     * @param id 服务器ID
     * @return 服务器 VO
     */
    McpServer getServerById(String id);

    /**
     * 查询所有 MCP 服务器列表
     *
     * @return 服务器VO列表
     */
    List<McpServer> listAll();

    /**
     * 测试 MCP 服务器连接
     *
     * @param id 服务器ID
     * @return 连接是否成功
     */
    boolean testConnection(String id);
}
