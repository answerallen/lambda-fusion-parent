package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.utils.AgentUtils.newStateWithCurrentNode;
import static com.lambda.fusion.ai.utils.AgentUtils.setCurrentNodeProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.agent.AgentGraph;
import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.agent.node.SubgraphNode;
import com.lambda.fusion.ai.service.WorkflowService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubgraphNodeTest {

    private SubgraphNode subgraphNode;

    @Mock
    private AgentGraphFactory mockGraphFactory;

    @Mock
    private WorkflowService mockWorkflowService;

    @Mock
    private AiProperties mockAiProperties;

    @BeforeEach
    void setUp() {
        subgraphNode = new SubgraphNode(mockGraphFactory, mockWorkflowService, Runnable::run, mockAiProperties);
    }

    @Test
    @DisplayName("测试节点名称")
    void testNodeName() {
        assertThat(subgraphNode.getName()).isEqualTo("SUBGRAPH");
    }

    @Test
    @DisplayName("测试空属性返回null")
    void testNullProperties() {
        AgentState state = new AgentState();
        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
        assertThat(result.nextNode()).isNull();
    }

    @Test
    @DisplayName("测试缺少子图定义")
    void testMissingSubgraphDefinition() {
        Map<String, Object> properties = new HashMap<>();

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result.nextNode()).isNull();
    }

    @Test
    @DisplayName("测试同步执行子图配置")
    void testSyncExecutionConfig() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");
        properties.put("inheritContext", false);
        properties.put("async", false);
        properties.put("propagateErrors", true);

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试异步执行配置")
    void testAsyncExecutionConfig() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");
        properties.put("inheritContext", true);
        properties.put("async", true);
        properties.put("propagateErrors", false);

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试类型安全-boolean属性恢复")
    void testTypeSafeBooleanPropertiesRecovery() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("isExecuting", Boolean.TRUE);
        contextMap.put("isCompleted", false);
        contextMap.put("isAsync", "true");
        contextMap.put("propagateErrors", 1);
        contextMap.put("startTime", System.currentTimeMillis());
        contextMap.put("completedAt", null);

        AgentState state = new AgentState();
        state.getAttributes().put("__subgraph_context__", contextMap);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");

        setCurrentNodeProperties(state, properties);

        assertThatCode(() -> subgraphNode.execute(state)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("测试类型安全-startTime恢复")
    void testTypeSafeStartTimeRecovery() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("isExecuting", false);
        contextMap.put("isCompleted", false);
        contextMap.put("isAsync", false);
        contextMap.put("propagateErrors", true);
        contextMap.put("startTime", 1712345678000L);
        contextMap.put("completedAt", null);

        AgentState state = new AgentState();
        state.getAttributes().put("__subgraph_context__", contextMap);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");

        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试子图上下文保存")
    void testSubgraphContextSave() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        subgraphNode.execute(state);

        Object context = state.getCurrentNodeProperties();
        assertThat(context).isNotNull();
        assertThat(context).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("测试propagateErrors默认值为true")
    void testPropagateErrorsDefaultValue() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        assertThatCode(() -> subgraphNode.execute(state)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("测试inheritContext配置")
    void testInheritContextConfig() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");
        properties.put("inheritContext", true);

        AgentState state = new AgentState();
        state.getAttributes().put("sharedData", "test");
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试错误信息存储")
    void testErrorStorage() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("isExecuting", false);
        contextMap.put("isCompleted", false);
        contextMap.put("isAsync", false);
        contextMap.put("propagateErrors", true);
        contextMap.put("startTime", System.currentTimeMillis());
        contextMap.put("completedAt", null);
        contextMap.put("error", "Test error message");

        AgentState state = new AgentState();
        state.getAttributes().put("__subgraph_context__", contextMap);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");

        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试使用subgraphId")
    void testSubgraphIdUsage() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphId", 1L);

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试使用subgraphName")
    void testSubgraphNameUsage() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphName", "testSubgraph");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试使用subgraphId-Integer类型")
    void testSubgraphIdIntegerType() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphId", 1);

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试使用subgraphId-String类型")
    void testSubgraphIdStringType() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphId", "123");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试inheritContext时inputMapping优先覆盖")
    void testInputMappingOverridesInheritedContext() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> inputMapping = new HashMap<>();
        inputMapping.put("x", "attributes.overrideX");

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");
        properties.put("inheritContext", true);
        properties.put("inputMapping", inputMapping);

        AgentState state = new AgentState();
        state.getAttributes().put("x", "parent");
        state.getAttributes().put("overrideX", "mapped");
        setCurrentNodeProperties(state, properties);

        subgraphNode.execute(state);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentState.class);
        verify(mockGraph).invoke(captor.capture());
        assertThat(captor.getValue().getAttributes().get("x")).isEqualTo("mapped");
    }

    @Test
    @DisplayName("测试异步子图超时后记录错误并返回nextNode")
    void testAsyncTimeoutStoresErrorAndReturnsNextNode() throws Exception {
        AgentGraph slowGraph = mock(AgentGraph.class);
        when(slowGraph.invoke(any(AgentState.class))).thenAnswer(invocation -> {
            Thread.sleep(300);
            return new AgentState();
        });
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(slowGraph);

        AiProperties props = new AiProperties();
        props.getAgent().getSubgraph().setAsyncTimeout(30);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        SubgraphNode timeoutNode = new SubgraphNode(mockGraphFactory, mockWorkflowService, executor, props);
        try {
            Map<String, Object> nodeProperties = new HashMap<>();
            nodeProperties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");
            nodeProperties.put("async", true);
            nodeProperties.put("propagateErrors", true);
            nodeProperties.put("nextNode", "afterTimeout");

            AgentState state = new AgentState();
            setCurrentNodeProperties(state, nodeProperties);

            AgentNode.ExecutionResult result = timeoutNode.execute(state);
            assertThat(result.nextNode()).isEqualTo("afterTimeout");
            assertThat(state.getAttributes().get("__subgraph_error__")).isEqualTo("子图执行超时");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("子图默认继承父消息与待执行工具")
    void shouldInheritMessagesAndPendingToolsByDefault() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(new AgentState());
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");

        AgentState state = newStateWithCurrentNode(null, properties, "父图问题");
        state.setPendingToolRequests(List.of(mock(ToolExecutionRequest.class)));

        subgraphNode.execute(state);

        var captor = org.mockito.ArgumentCaptor.forClass(AgentState.class);
        verify(mockGraph).invoke(captor.capture());
        assertThat(captor.getValue().getMessages()).hasSize(1);
        assertThat(captor.getValue().getPendingToolRequests()).hasSize(1);
    }

    @Test
    @DisplayName("子图会回写新增消息并在无 nextNode 时传播 finished")
    void shouldAppendMessagesAndPropagateFinished() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        AgentState subgraphOutput = new AgentState();
        subgraphOutput.addMessage(UserMessage.from("父图问题"));
        subgraphOutput.addMessage(AiMessage.from("子图答案"));
        subgraphOutput.setFinished(true);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(subgraphOutput);
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");

        AgentState state = newStateWithCurrentNode(null, properties, "父图问题");

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result.nextNode()).isNull();
        assertThat(state.getMessages()).hasSize(2);
        assertThat(state.getMessages().getLast()).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) state.getMessages().getLast()).text()).isEqualTo("子图答案");
        assertThat(state.isFinished()).isTrue();
    }

    @Test
    @DisplayName("配置 nextNode 时默认不传播 finished")
    void shouldNotPropagateFinishedWhenNextNodeConfigured() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        AgentState subgraphOutput = new AgentState();
        subgraphOutput.setFinished(true);
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(subgraphOutput);
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");
        properties.put("nextNode", "afterSubgraph");

        AgentState state = newStateWithCurrentNode(null, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result.nextNode()).isEqualTo("afterSubgraph");
        assertThat(state.isFinished()).isFalse();
    }

    @Test
    @DisplayName("子图可回写待执行工具请求")
    void shouldPropagatePendingToolRequestsFromSubgraph() throws Exception {
        AgentGraph mockGraph = mock(AgentGraph.class);
        AgentState subgraphOutput = new AgentState();
        subgraphOutput.setPendingToolRequests(List.of(mock(ToolExecutionRequest.class)));
        when(mockGraph.invoke(any(AgentState.class))).thenReturn(subgraphOutput);
        when(mockGraphFactory.buildFromDefinition(anyString())).thenReturn(mockGraph);

        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphDefinition", "{\"nodes\":[],\"edges\":[]}");

        AgentState state = newStateWithCurrentNode(null, properties);

        subgraphNode.execute(state);

        assertThat(state.getPendingToolRequests()).hasSize(1);
    }
}
