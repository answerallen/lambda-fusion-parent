package com.lambda.fusion.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.ai.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.model.WorkflowExecutionResult;
import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.service.WorkflowExecutionService;
import com.lambda.fusion.ai.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1/ai/workflows")
@Tag(name = "Agent 工作流引擎管理")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowExecutionService workflowExecutionService;
    private final SseEmitterManager sseEmitterManager;

    @PostMapping
    @Operation(summary = "保存或更新工作流配置")
    public Boolean saveWorkflow(@RequestBody WorkflowEntity entity) {
        if (entity.getId() == null) {
            return workflowService.save(entity);
        } else {
            return workflowService.updateById(entity);
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取工作流图编排详情")
    public WorkflowEntity getWorkflow(@PathVariable Long id) {
        return workflowService.getById(id);
    }

    @GetMapping
    @Operation(summary = "获取所有编排工作流列表")
    public List<WorkflowEntity> listWorkflows() {
        return workflowService.list();
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "执行工作流")
    public WorkflowExecutionResult execute(@PathVariable Long id, @RequestBody WorkflowExecutionRequest request) {
        return workflowExecutionService.execute(id, request);
    }

    @PostMapping("/{id}/execute/stream")
    @Operation(summary = "流式执行工作流")
    public SseEmitter executeStream(@PathVariable Long id, @RequestBody WorkflowExecutionRequest request) {
        String clientId = "workflow_" + id + "_" + System.currentTimeMillis();
        SseEmitter emitter = sseEmitterManager.createEmitter(clientId);

        workflowExecutionService.executeStream(
                id, request, new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        sseEmitterManager.sendEvent(clientId, "token", token);
                    }

                    @Override
                    public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                        sseEmitterManager.sendEvent(clientId, "finish", response);
                    }

                    @Override
                    public void onError(Throwable error) {
                        sseEmitterManager.sendEvent(clientId, "error", error.getMessage());
                    }
                });

        return emitter;
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "查询执行结果")
    public WorkflowExecutionResult getExecutionResult(@PathVariable String executionId) {
        return workflowExecutionService.getExecutionResult(executionId);
    }

    @GetMapping("/{id}/executions")
    @Operation(summary = "查询工作流执行历史")
    public Page<WorkflowExecutionResult> listExecutions(
            @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {
        return workflowExecutionService.listExecutions(id, pageNum, pageSize);
    }
}
