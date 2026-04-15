package com.lambda.fusion.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.model.WorkflowExecutionRequest;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class WorkflowExecutionServiceImplTest {

    @Test
    void shouldBuildGraphOptionsFromRequest() throws Exception {
        WorkflowExecutionServiceImpl service = newService(new MemorySaver());
        WorkflowExecutionRequest request = new WorkflowExecutionRequest();
        request.setCheckpointEnabled(true);
        request.setInterruptBefore("tools");
        request.setInterruptAfter("review");
        request.setReleaseThread(false);
        request.setMaxIterations(64);

        var options = service.buildGraphBuildOptions(request);

        assertThat(options).isNotNull();
        assertThat(options.getMaxIterations()).isEqualTo(64);
        assertThat(options.getCompileConfig()).isNotNull();
        assertThat(options.getCompileConfig().checkpointSaver()).isPresent();
        assertThat(options.getCompileConfig().releaseThread()).isFalse();
        assertThat(options.getCompileConfig().interruptsBefore()).contains("tools");
        assertThat(options.getCompileConfig().interruptsAfter()).contains("review");
    }

    @Test
    void shouldFailWhenCheckpointEnabledWithoutSaver() {
        WorkflowExecutionServiceImpl service = newService(null);
        WorkflowExecutionRequest request = new WorkflowExecutionRequest();
        request.setCheckpointEnabled(true);

        assertThatThrownBy(() -> service.buildCompileConfig(request)).isInstanceOf(AiBusinessException.class);
    }

    @Test
    void shouldResolveThreadIdFromRequestOrSession() throws Exception {
        WorkflowExecutionServiceImpl service = newService(new MemorySaver());
        WorkflowExecutionRequest explicit = new WorkflowExecutionRequest();
        explicit.setThreadId("thread-1");

        WorkflowExecutionRequest fallback = new WorkflowExecutionRequest();
        fallback.setCheckpointEnabled(true);
        fallback.setSessionId("session-1");

        assertThat(service.resolveThreadId(explicit, "execution-1")).isEqualTo("thread-1");
        assertThat(service.resolveThreadId(fallback, "execution-1")).isEqualTo("session-1");

        RunnableConfig runnableConfig = service.buildRunnableConfig("thread-1");
        assertThat(runnableConfig).isNotNull();
        assertThat(runnableConfig.threadId()).contains("thread-1");
    }

    private WorkflowExecutionServiceImpl newService(MemorySaver memorySaver) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (memorySaver != null) {
            beanFactory.registerSingleton("workflowCheckpointSaver", memorySaver);
        }
        ObjectProvider<MemorySaver> provider = beanFactory.getBeanProvider(MemorySaver.class);
        return new WorkflowExecutionServiceImpl(null, null, null, null, null, null, null, provider);
    }
}
