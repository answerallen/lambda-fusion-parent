package com.lambda.fusion.ai.controller;

import com.lambda.fusion.ai.commons.agent.tools.AgentToolProvider;
import com.lambda.fusion.ai.model.CreateMcpServer;
import com.lambda.fusion.ai.model.McpServer;
import com.lambda.fusion.ai.model.UpdateMcpServer;
import com.lambda.fusion.ai.service.McpServerService;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP（Model Context Protocol）Server 管理接口
 *
 * @author Jin
 */
@RestController
@RequestMapping("/v1/mcp/servers")
@Tag(name = "MCP Server 管理")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;
    private final AgentToolProvider agentToolProvider;

    @PostMapping
    @Operation(summary = "注册 MCP 服务器")
    public String create(@Valid @RequestBody CreateMcpServer request) {
        return mcpServerService.create(request);
    }

    @GetMapping
    @Operation(summary = "查询所有 MCP 服务器")
    public List<McpServer> listAll() {
        return mcpServerService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询 MCP 服务器详情")
    public McpServer getById(@PathVariable String id) {
        return mcpServerService.getServerById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 MCP 服务器配置")
    public void update(@PathVariable String id, @Valid @RequestBody UpdateMcpServer request) {
        mcpServerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 MCP 服务器")
    public void delete(@PathVariable String id) {
        mcpServerService.delete(id);
    }

    @PostMapping("/{id}/connect/test")
    @Operation(summary = "测试 MCP 服务器连接")
    public boolean testConnection(@PathVariable String id) {
        return mcpServerService.testConnection(id);
    }

    @GetMapping("/tools")
    @Operation(summary = "查询所有已注册 MCP 工具列表（含本地@Tool）")
    public List<ToolSpecification> listAllTools() {
        return agentToolProvider.getToolSpecifications();
    }

    @PostMapping("/tools/refresh")
    @Operation(summary = "手动刷新 MCP 工具列表")
    public void refreshTools() {
        agentToolProvider.refreshMcpTools();
    }
}
