package com.lambda.fusion.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.cloud.sse.SseEmitterManager;
import com.lambda.fusion.ai.model.WorkflowExecutionResult;
import com.lambda.fusion.ai.model.WorkflowExecutionStatus;
import com.lambda.fusion.ai.model.WorkflowResumeRequest;
import com.lambda.fusion.ai.service.WorkflowExecutionService;
import com.lambda.fusion.ai.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

    @Mock
    private WorkflowService workflowService;

    @Mock
    private WorkflowExecutionService workflowExecutionService;

    @Mock
    private SseEmitterManager sseEmitterManager;

    @Test
    void shouldDelegateResumeRequestToExecutionService() {
        WorkflowController controller =
                new WorkflowController(workflowService, workflowExecutionService, sseEmitterManager);
        WorkflowResumeRequest request = new WorkflowResumeRequest();
        request.setThreadId("thread-1");
        request.setCheckpointId("cp-1");
        request.setMessage("continue");
        WorkflowExecutionResult expected = WorkflowExecutionResult.builder()
                .id("exec-1")
                .threadId("thread-1")
                .checkpointId("cp-2")
                .build();
        when(workflowExecutionService.resume("wf-1", request)).thenReturn(expected);

        WorkflowExecutionResult actual = controller.resume("wf-1", request);

        assertThat(actual).isSameAs(expected);
        verify(workflowExecutionService).resume("wf-1", request);
    }

    @Test
    void shouldDelegateThreadStatusQueryToExecutionService() {
        WorkflowController controller =
                new WorkflowController(workflowService, workflowExecutionService, sseEmitterManager);
        WorkflowExecutionStatus expected = WorkflowExecutionStatus.builder()
                .threadId("thread-1")
                .checkpointId("cp-9")
                .status("WAITING_FOR_INPUT")
                .waitingForInput(true)
                .build();
        when(workflowExecutionService.getExecutionStatus("wf-1", "thread-1", "cp-9"))
                .thenReturn(expected);

        WorkflowExecutionStatus actual = controller.getExecutionStatus("wf-1", "thread-1", "cp-9");

        assertThat(actual).isSameAs(expected);
        verify(workflowExecutionService).getExecutionStatus("wf-1", "thread-1", "cp-9");
    }
}
