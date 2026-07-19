package com.lambda.fusion.ai.mcp.controller;

import com.lambda.fusion.ai.agent.runtime.McpClientAdapter;
import com.lambda.fusion.ai.agent.runtime.ToolToolkitAdapter;
import com.lambda.fusion.ai.mcp.model.CreateMcpServer;
import com.lambda.fusion.ai.mcp.model.McpServer;
import com.lambda.fusion.ai.mcp.model.UpdateMcpServer;
import com.lambda.fusion.ai.mcp.service.McpServerService;
import io.agentscope.core.model.ToolSchema;
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
    private final ToolToolkitAdapter toolToolkitAdapter;
    private final McpClientAdapter mcpClientAdapter;

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
    @Operation(summary = "查询所有已注册本地 @Tool 列表（MCP 工具为 per-agent 装配，不在此列）")
    public List<ToolSchema> listAllTools() {
        return toolToolkitAdapter.getToolSchemas();
    }

    @PostMapping("/tools/refresh")
    @Operation(summary = "手动刷新本地 @Tool 扫描 + 清除 MCP 客户端缓存")
    public void refreshTools() {
        toolToolkitAdapter.refresh();
        mcpClientAdapter.invalidateAll();
    }
}
