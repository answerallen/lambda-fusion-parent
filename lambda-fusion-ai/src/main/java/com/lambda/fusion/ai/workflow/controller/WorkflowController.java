package com.lambda.fusion.ai.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.ai.workflow.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.workflow.model.WorkflowExecutionResult;
import com.lambda.fusion.ai.workflow.model.WorkflowExecutionStatus;
import com.lambda.fusion.ai.workflow.model.WorkflowResumeRequest;
import com.lambda.fusion.ai.workflow.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.workflow.service.WorkflowExecutionService;
import com.lambda.fusion.ai.workflow.service.WorkflowService;
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
    public WorkflowEntity getWorkflow(@PathVariable String id) {
        return workflowService.getById(id);
    }

    @GetMapping
    @Operation(summary = "获取所有编排工作流列表")
    public List<WorkflowEntity> listWorkflows() {
        return workflowService.list();
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "执行工作流")
    public WorkflowExecutionResult execute(@PathVariable String id, @RequestBody WorkflowExecutionRequest request) {
        sanitizeRequestContext(request);
        return workflowExecutionService.execute(id, request);
    }

    @PostMapping("/{id}/execute/stream")
    @Operation(summary = "流式执行工作流")
    public SseEmitter executeStream(@PathVariable String id, @RequestBody WorkflowExecutionRequest request) {
        sanitizeRequestContext(request);
        String clientId = "workflow_" + id + "_" + System.currentTimeMillis();
        SseEmitter emitter = sseEmitterManager.createEmitter(clientId);
        try {
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
        } catch (Exception e) {
            sseEmitterManager.sendEvent(clientId, "error", e.getMessage());
        }

        return emitter;
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "恢复工作流执行")
    public WorkflowExecutionResult resume(@PathVariable String id, @RequestBody WorkflowResumeRequest request) {
        sanitizeResumeRequestContext(request);
        return workflowExecutionService.resume(id, request);
    }

    private void sanitizeRequestContext(WorkflowExecutionRequest request) {
        if (request == null) {
            return;
        }
        request.setUserId(OperatorUtils.getOperator().getName());
        request.setTenantId(OperatorUtils.getOperator().getTenantId());
        request.setCalledFromChat(false);
    }

    private void sanitizeResumeRequestContext(WorkflowResumeRequest request) {
        if (request == null) {
            return;
        }
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "查询执行结果")
    public WorkflowExecutionResult getExecutionResult(@PathVariable String executionId) {
        return workflowExecutionService.getExecutionResult(executionId);
    }

    @GetMapping("/{id}/threads/{threadId}/status")
    @Operation(summary = "查询工作流线程状态")
    public WorkflowExecutionStatus getExecutionStatus(
            @PathVariable String id,
            @PathVariable String threadId,
            @Parameter(description = "指定checkpoint ID") @RequestParam(required = false) String checkpointId) {
        return workflowExecutionService.getExecutionStatus(id, threadId, checkpointId);
    }

    @GetMapping("/{id}/executions")
    @Operation(summary = "查询工作流执行历史")
    public Page<WorkflowExecutionResult> listExecutions(
            @PathVariable String id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {
        return workflowExecutionService.listExecutions(id, pageNum, pageSize);
    }
}
