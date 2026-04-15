package com.lambda.fusion.ai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.utils.CostCalculator;
import com.lambda.fusion.ai.mapper.LlmModelMapper;
import com.lambda.fusion.ai.mapper.WorkflowExecutionMapper;
import com.lambda.fusion.ai.mapper.WorkflowMapper;
import com.lambda.fusion.ai.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.model.WorkflowExecutionResult;
import com.lambda.fusion.ai.model.WorkflowExecutionStatus;
import com.lambda.fusion.ai.model.WorkflowResumeRequest;
import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.model.entity.WorkflowExecutionEntity;
import com.lambda.fusion.ai.service.AtomicSessionUpdateService;
import com.lambda.fusion.core.utils.AuthUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionServiceImplTest {

    @Mock
    private WorkflowMapper workflowMapper;

    @Mock
    private WorkflowExecutionMapper executionMapper;

    @Mock
    private com.lambda.fusion.ai.commons.agent.factory.AgentGraphFactory agentGraphFactory;

    @Mock
    private AtomicSessionUpdateService atomicSessionUpdateService;

    @Mock
    private CostCalculator costCalculator;

    @Mock
    private LlmModelMapper llmModelMapper;

    @Mock
    private AgentGraph graph;

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

    @Test
    void shouldBuildResumeGraphOptionsWithCheckpointSaver() {
        WorkflowExecutionServiceImpl service = newService(new MemorySaver());
        WorkflowResumeRequest request = new WorkflowResumeRequest();
        request.setInterruptBefore("wait_input");
        request.setReleaseThread(false);
        request.setMaxIterations(12);

        var options = service.buildGraphBuildOptions(request);

        assertThat(options).isNotNull();
        assertThat(options.getMaxIterations()).isEqualTo(12);
        assertThat(options.getCompileConfig()).isNotNull();
        assertThat(options.getCompileConfig().checkpointSaver()).isPresent();
        assertThat(options.getCompileConfig().interruptsBefore()).contains("wait_input");
        assertThat(options.getCompileConfig().releaseThread()).isFalse();
    }

    @Test
    void shouldBuildRunnableConfigWithCheckpointAndNextNode() {
        WorkflowExecutionServiceImpl service = newService(new MemorySaver());

        RunnableConfig runnableConfig = service.buildRunnableConfig("thread-1", "cp-1", "node-b");

        assertThat(runnableConfig).isNotNull();
        assertThat(runnableConfig.threadId()).contains("thread-1");
        assertThat(runnableConfig.checkPointId()).contains("cp-1");
    }

    @Test
    void shouldBuildResumeStateUpdateFromRequest() {
        WorkflowExecutionServiceImpl service = newService(new MemorySaver());
        WorkflowResumeRequest request = new WorkflowResumeRequest();
        request.setSessionId("session-1");
        request.setKbId("kb-1");
        request.setLlmModelId("model-1");
        request.setMessage("please continue");
        request.setTraceEnabled(false);
        request.setInputParams(Map.of("approval", true));

        AgentState snapshotState = new AgentState();
        snapshotState.setAttributes(Map.of("existing", "value"));

        Map<String, Object> update = service.buildResumeStateUpdate(snapshotState, request, "exec-1", "thread-1");

        assertThat(update).containsEntry(AgentGraph.LangGraphRuntimeState.SESSION_ID_KEY, "session-1");
        assertThat(update).containsEntry(AgentGraph.LangGraphRuntimeState.KB_ID_KEY, "kb-1");
        assertThat(update).containsEntry(AgentGraph.LangGraphRuntimeState.LLM_MODEL_ID_KEY, "model-1");
        assertThat(update.get(AgentGraph.LangGraphRuntimeState.MESSAGES_KEY))
                .asList()
                .singleElement()
                .isInstanceOf(UserMessage.class);
        assertThat(update.get(AgentGraph.LangGraphRuntimeState.ATTRIBUTES_KEY))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("executionId", "exec-1")
                .containsEntry("threadId", "thread-1")
                .containsEntry("existing", "value")
                .containsEntry("_traceEnabled", false);
    }

    @Test
    void shouldRequireThreadIdForResumeOperations() {
        WorkflowExecutionServiceImpl service = newService(new MemorySaver());

        assertThatThrownBy(() -> service.requireThreadId(" "))
                .isInstanceOf(AiBusinessException.class)
                .hasMessageContaining("线程ID");
    }

    @Test
    void shouldResumeExecutionAndReturnWaitingState() throws Exception {
        WorkflowExecutionServiceImpl service = newMockedService(new MemorySaver());
        WorkflowResumeRequest request = new WorkflowResumeRequest();
        request.setThreadId("thread-1");
        request.setCheckpointId("cp-1");
        request.setMessage("approve and continue");
        request.setInputParams(Map.of("approved", true));

        try (MockedStatic<AuthUtils> authUtils = org.mockito.Mockito.mockStatic(AuthUtils.class)) {
            authUtils.when(AuthUtils::getTenantId).thenReturn(null);

            WorkflowEntity workflow = workflow("wf-1");
            when(workflowMapper.selectById("wf-1")).thenReturn(workflow);
            when(agentGraphFactory.buildFromDefinition(eq(workflow.getGraphJson()), any()))
                    .thenReturn(graph);
            when(executionMapper.insert(any(WorkflowExecutionEntity.class))).thenAnswer(invocation -> {
                WorkflowExecutionEntity entity = invocation.getArgument(0);
                entity.setId("exec-1");
                return 1;
            });

            AgentState snapshotState = new AgentState();
            snapshotState.setAttributes(new HashMap<>(Map.of("existing", "value")));
            AgentState resumedState = new AgentState();
            resumedState.setFinished(false);
            resumedState.setMessages(new ArrayList<>(java.util.List.of(AiMessage.from("need confirmation"))));

            when(graph.stateSnapshotOf(any()))
                    .thenReturn(Optional.of(snapshot("cp-1", "waitApproval", snapshotState)))
                    .thenReturn(Optional.of(snapshot("cp-2", "toolNode", resumedState)));
            when(graph.resumeOptional(anyMap(), any())).thenReturn(Optional.of(resumedState));

            WorkflowExecutionResult result = service.resume("wf-1", request);

            assertThat(result.getThreadId()).isEqualTo("thread-1");
            assertThat(result.getCheckpointId()).isEqualTo("cp-2");
            assertThat(result.getNextNode()).isEqualTo("toolNode");
            assertThat(result.getStatus()).isEqualTo("WAITING_FOR_INPUT");
            assertThat(result.getInterrupted()).isTrue();
            assertThat(result.getFinished()).isFalse();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> stateUpdateCaptor = ArgumentCaptor.forClass(Map.class);
            verify(graph).resumeOptional(stateUpdateCaptor.capture(), any(RunnableConfig.class));
            assertThat(stateUpdateCaptor.getValue())
                    .containsEntry(
                            AgentGraph.LangGraphRuntimeState.MESSAGES_KEY,
                            java.util.List.of(UserMessage.from("approve and continue")));
            assertThat(stateUpdateCaptor.getValue().get(AgentGraph.LangGraphRuntimeState.ATTRIBUTES_KEY))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("executionId", "exec-1")
                    .containsEntry("threadId", "thread-1")
                    .containsEntry("existing", "value");

            ArgumentCaptor<WorkflowExecutionEntity> entityCaptor =
                    ArgumentCaptor.forClass(WorkflowExecutionEntity.class);
            verify(executionMapper).updateById(entityCaptor.capture());
            assertThat(entityCaptor.getValue().getStatus()).isEqualTo("WAITING_FOR_INPUT");
            assertThat(entityCaptor.getValue().getCurrentStep()).isEqualTo("toolNode");
        }
    }

    @Test
    void shouldPreserveCheckpointNotFoundWhenResumeSnapshotMissing() throws Exception {
        WorkflowExecutionServiceImpl service = newMockedService(new MemorySaver());
        WorkflowResumeRequest request = new WorkflowResumeRequest();
        request.setThreadId("thread-1");

        try (MockedStatic<AuthUtils> authUtils = org.mockito.Mockito.mockStatic(AuthUtils.class)) {
            authUtils.when(AuthUtils::getTenantId).thenReturn(null);

            WorkflowEntity workflow = workflow("wf-1");
            when(workflowMapper.selectById("wf-1")).thenReturn(workflow);
            when(agentGraphFactory.buildFromDefinition(eq(workflow.getGraphJson()), any()))
                    .thenReturn(graph);
            when(executionMapper.insert(any(WorkflowExecutionEntity.class))).thenAnswer(invocation -> {
                WorkflowExecutionEntity entity = invocation.getArgument(0);
                entity.setId("exec-1");
                return 1;
            });
            when(graph.stateSnapshotOf(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resume("wf-1", request))
                    .isInstanceOf(AiBusinessException.class)
                    .hasMessageContaining("checkpoint");
        }
    }

    @Test
    void shouldMapExecutionStatusFromCheckpointSnapshot() throws Exception {
        WorkflowExecutionServiceImpl service = newMockedService(new MemorySaver());
        try (MockedStatic<AuthUtils> authUtils = org.mockito.Mockito.mockStatic(AuthUtils.class)) {
            authUtils.when(AuthUtils::getTenantId).thenReturn(null);

            WorkflowEntity workflow = workflow("wf-1");
            when(workflowMapper.selectById("wf-1")).thenReturn(workflow);
            when(agentGraphFactory.buildFromDefinition(eq(workflow.getGraphJson()), any()))
                    .thenReturn(graph);

            AgentState state = new AgentState();
            state.setFinished(false);
            state.setMessages(new ArrayList<>(java.util.List.of(AiMessage.from("waiting user input"))));
            state.setAttributes(
                    new HashMap<>(Map.of("executionId", "exec-9", AgentGraph.CURRENT_NODE_ID_ATTRIBUTE, "reviewNode")));

            when(graph.stateSnapshotOf(any())).thenReturn(Optional.of(snapshot("cp-9", "collectInput", state)));

            WorkflowExecutionStatus status = service.getExecutionStatus("wf-1", "thread-9", null);

            assertThat(status.getThreadId()).isEqualTo("thread-9");
            assertThat(status.getCheckpointId()).isEqualTo("cp-9");
            assertThat(status.getExecutionId()).isEqualTo("exec-9");
            assertThat(status.getCurrentNodeId()).isEqualTo("reviewNode");
            assertThat(status.getNextNode()).isEqualTo("collectInput");
            assertThat(status.getStatus()).isEqualTo("WAITING_FOR_INPUT");
            assertThat(status.getInterrupted()).isTrue();
            assertThat(status.getWaitingForInput()).isTrue();
            assertThat(status.getAnswer()).isEqualTo("waiting user input");
        }
    }

    private WorkflowExecutionServiceImpl newService(MemorySaver memorySaver) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (memorySaver != null) {
            beanFactory.registerSingleton("workflowCheckpointSaver", memorySaver);
        }
        ObjectProvider<MemorySaver> provider = beanFactory.getBeanProvider(MemorySaver.class);
        return new WorkflowExecutionServiceImpl(null, null, null, null, null, null, null, provider);
    }

    private WorkflowExecutionServiceImpl newMockedService(MemorySaver memorySaver) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (memorySaver != null) {
            beanFactory.registerSingleton("workflowCheckpointSaver", memorySaver);
        }
        ObjectProvider<MemorySaver> provider = beanFactory.getBeanProvider(MemorySaver.class);
        return new WorkflowExecutionServiceImpl(
                workflowMapper,
                executionMapper,
                agentGraphFactory,
                new ObjectMapper(),
                atomicSessionUpdateService,
                costCalculator,
                llmModelMapper,
                provider);
    }

    @SuppressWarnings("unchecked")
    private StateSnapshot<AgentGraph.LangGraphRuntimeState> snapshot(
            String checkpointId, String nextNode, AgentState state) {
        Checkpoint checkpoint = Checkpoint.builder()
                .id(checkpointId)
                .state(toRuntimeStateData(state, nextNode))
                .nodeId("currentNode")
                .nextNodeId(nextNode)
                .build();
        return StateSnapshot.of(
                checkpoint,
                RunnableConfig.builder().threadId("thread").build(),
                initData -> newRuntimeState((Map<String, Object>) initData));
    }

    private WorkflowEntity workflow(String id) {
        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setId(id);
        workflow.setGraphJson("{\"nodes\":[],\"edges\":[]}");
        workflow.setTenantId(null);
        return workflow;
    }

    private Map<String, Object> toRuntimeStateData(AgentState state, String nextNode) {
        Map<String, Object> data = new HashMap<>(AgentGraph.LangGraphRuntimeState.toInput(state));
        data.put(AgentGraph.LangGraphRuntimeState.NEXT_NODE_KEY, nextNode);
        return data;
    }

    @SuppressWarnings("unchecked")
    private AgentGraph.LangGraphRuntimeState newRuntimeState(Map<String, Object> initData) {
        try {
            var constructor = AgentGraph.LangGraphRuntimeState.class.getDeclaredConstructor(Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(initData == null ? Collections.emptyMap() : initData);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建 LangGraphRuntimeState 测试实例", e);
        }
    }
}
