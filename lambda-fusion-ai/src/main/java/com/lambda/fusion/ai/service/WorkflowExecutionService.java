package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.model.WorkflowExecutionResult;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public interface WorkflowExecutionService {

    WorkflowExecutionResult execute(String workflowId, WorkflowExecutionRequest request);

    void executeStream(String workflowId, WorkflowExecutionRequest request, StreamingChatResponseHandler handler);

    WorkflowExecutionResult getExecutionResult(String executionId);

    Page<WorkflowExecutionResult> listExecutions(String workflowId, int pageNum, int pageSize);
}
