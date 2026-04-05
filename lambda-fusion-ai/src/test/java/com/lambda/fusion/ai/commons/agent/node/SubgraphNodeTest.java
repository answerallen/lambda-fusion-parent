package com.lambda.fusion.ai.commons.agent.node;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.service.WorkflowService;
import java.util.*;
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

    private void setNodeProperties(AgentState state, Map<String, Object> properties) {
        state.getAttributes().put(AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE, properties);
    }

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
        setNodeProperties(state, properties);

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
        setNodeProperties(state, properties);

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
        setNodeProperties(state, properties);

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

        setNodeProperties(state, properties);

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

        setNodeProperties(state, properties);

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
        setNodeProperties(state, properties);

        subgraphNode.execute(state);

        Object context = state.getAttributes().get("__subgraph_context__");
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
        setNodeProperties(state, properties);

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
        setNodeProperties(state, properties);

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

        setNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试使用subgraphId")
    void testSubgraphIdUsage() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphId", 1L);

        AgentState state = new AgentState();
        setNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试使用subgraphName")
    void testSubgraphNameUsage() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphName", "testSubgraph");

        AgentState state = new AgentState();
        setNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试使用subgraphId-Integer类型")
    void testSubgraphIdIntegerType() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphId", 1);

        AgentState state = new AgentState();
        setNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试使用subgraphId-String类型")
    void testSubgraphIdStringType() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("subgraphId", "123");

        AgentState state = new AgentState();
        setNodeProperties(state, properties);

        AgentNode.ExecutionResult result = subgraphNode.execute(state);

        assertThat(result).isNotNull();
    }
}
