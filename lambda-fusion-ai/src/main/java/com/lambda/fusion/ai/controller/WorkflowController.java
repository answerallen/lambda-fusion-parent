package com.lambda.fusion.ai.controller;

import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ai/workflows")
@Tag(name = "Agent 工作流引擎管理")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

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
}
