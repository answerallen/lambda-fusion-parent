package com.lambda.fusion.ai.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.workflow.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.workflow.model.WorkflowExecutionResult;
import com.lambda.fusion.ai.workflow.model.WorkflowExecutionStatus;
import com.lambda.fusion.ai.workflow.model.WorkflowResumeRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public interface WorkflowExecutionService {

    WorkflowExecutionResult execute(String workflowId, WorkflowExecutionRequest request);

    void executeStream(String workflowId, WorkflowExecutionRequest request, StreamingChatResponseHandler handler);

    WorkflowExecutionResult resume(String workflowId, WorkflowResumeRequest request);

    WorkflowExecutionResult getExecutionResult(String executionId);

    WorkflowExecutionStatus getExecutionStatus(String workflowId, String threadId, String checkpointId);

    Page<WorkflowExecutionResult> listExecutions(String workflowId, int pageNum, int pageSize);
}
