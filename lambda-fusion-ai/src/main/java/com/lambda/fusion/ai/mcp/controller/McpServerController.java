package com.lambda.fusion.ai.mcp.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.logger.annotation.OperationLog;
import com.lambda.fusion.ai.mcp.model.CreateMcpServer;
import com.lambda.fusion.ai.mcp.model.McpServerPage;
import com.lambda.fusion.ai.mcp.model.UpdateMcpServer;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import com.lambda.fusion.ai.mcp.service.McpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaCheckRole("ROLE_DEV")
@Tag(name = "MCP 服务管理")
@RestController
@RequestMapping("/v1/ai/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;

    @Operation(summary = "分页查询 MCP 服务")
    @GetMapping("/page")
    public Page<McpServerEntity> page(@Valid McpServerPage query) {
        return mcpServerService.page(query);
    }

    @Operation(summary = "查询 MCP 服务详情")
    @GetMapping("/{id}")
    public McpServerEntity get(@Parameter(description = "服务ID", required = true) @PathVariable String id) {
        return mcpServerService.get(id);
    }

    @OperationLog
    @Operation(summary = "新增 MCP 服务")
    @PostMapping
    public McpServerEntity create(@RequestBody @Valid CreateMcpServer dto) {
        return mcpServerService.create(dto);
    }

    @OperationLog
    @Operation(summary = "更新 MCP 服务")
    @PutMapping("/{id}")
    public void update(
            @Parameter(description = "服务ID", required = true) @PathVariable String id,
            @RequestBody @Valid UpdateMcpServer dto) {
        mcpServerService.update(id, dto);
    }

    @OperationLog
    @Operation(summary = "删除 MCP 服务")
    @DeleteMapping("/{id}")
    public void delete(@Parameter(description = "服务ID", required = true) @PathVariable String id) {
        mcpServerService.delete(id);
    }

    @Operation(summary = "测试 MCP 服务连通性，返回发现的工具数量")
    @PostMapping("/{id}/test")
    public int test(@Parameter(description = "服务ID", required = true) @PathVariable String id) {
        return mcpServerService.testConnection(id);
    }
}
