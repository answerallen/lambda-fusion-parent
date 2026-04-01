package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.model.WorkflowExecutionResult;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public interface WorkflowExecutionService {

    WorkflowExecutionResult execute(Long workflowId, WorkflowExecutionRequest request);

    void executeStream(Long workflowId, WorkflowExecutionRequest request, StreamingChatResponseHandler handler);

    WorkflowExecutionResult getExecutionResult(String executionId);

    Page<WorkflowExecutionResult> listExecutions(Long workflowId, int pageNum, int pageSize);
}
