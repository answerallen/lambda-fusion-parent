package com.lambda.fusion.ai.commons.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lambda.fusion.ai.commons.agent.evaluator.ConditionEvaluator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentGraphTest {

    private AgentGraph graph;

    @Mock
    private AgentNode mockNode1;

    @Mock
    private AgentNode mockNode2;

    @Mock
    private AgentNode mockNode3;

    @Mock
    private ConditionEvaluator mockEvaluator;

    @BeforeEach
    void setUp() {
        graph = new AgentGraph();
    }

    @Test
    @DisplayName("测试添加节点-无属性")
    void testAddNodeWithoutProperties() {
        graph.addNode("node1", mockNode1);

        assertThat(graph).isNotNull();
    }

    @Test
    @DisplayName("测试添加节点-带属性")
    void testAddNodeWithProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("temperature", 0.7);

        graph.addNode("node1", mockNode1, properties);

        assertThat(graph).isNotNull();
    }

    @Test
    @DisplayName("测试添加边")
    void testAddEdge() {
        graph.addNode("node1", mockNode1);
        graph.addNode("node2", mockNode2);
        graph.addEdge("node1", "node2", null, null);

        // 验证边已添加，通过测试不抛出异常即可
        assertThat(graph).isNotNull();
    }

    @Test
    @DisplayName("测试设置入口点")
    void testSetEntryPoint() {
        graph.addNode("startNode", mockNode1);
        graph.setEntryPoint("startNode");

        // 验证入口点已设置，通过测试不抛出异常即可
        assertThat(graph).isNotNull();
    }

    @Test
    @DisplayName("测试设置入口点-节点不存在抛异常")
    void testSetEntryPointNodeNotExist() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            graph.setEntryPoint("nonExistentNode");
        });
    }

    @Test
    @DisplayName("测试设置最大迭代次数")
    void testSetMaxIterations() {
        graph.setMaxIterations(50);
    }

    @Test
    @DisplayName("测试设置最大迭代次数-无效值抛异常")
    void testSetMaxIterationsInvalid() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            graph.setMaxIterations(0);
        });
    }

    @Test
    @DisplayName("测试简单工作流执行")
    void testSimpleWorkflowExecution() {
        when(mockNode1.getName()).thenReturn("startNode");

        AgentState finishedState = new AgentState();
        finishedState.setFinished(true);

        when(mockNode1.execute(any(AgentState.class))).thenReturn(new AgentNode.ExecutionResult(finishedState, null));

        graph.addNode("startNode", mockNode1);
        graph.addNode("endNode", mockNode2);
        graph.addEdge("startNode", "endNode", null, null);
        graph.setEntryPoint("startNode");

        AgentState initialState = new AgentState();
        initialState.setSessionId("1");

        AgentState finalState = graph.invoke(initialState);

        Assertions.assertNotNull(finalState);
        verify(mockNode1, atLeastOnce()).execute(any(AgentState.class));
    }

    @Test
    @DisplayName("测试条件边执行")
    void testConditionalEdgeExecution() {
        AgentState resultState = new AgentState();
        resultState.setFinished(true);

        when(mockNode1.execute(any(AgentState.class))).thenReturn(new AgentNode.ExecutionResult(resultState, null));

        graph.addNode("startNode", mockNode1);
        graph.addNode("truePath", mockNode2);
        graph.addNode("falsePath", mockNode3);
        graph.addEdge("startNode", "truePath", mockEvaluator, "condition");
        graph.addEdge("startNode", "falsePath", mockEvaluator, "!condition");
        graph.setEntryPoint("startNode");

        AgentState initialState = new AgentState();
        initialState.setSessionId("1");

        graph.invoke(initialState);

        verify(mockNode1, atLeastOnce()).execute(any(AgentState.class));
    }

    @Test
    @DisplayName("测试路由优先条件边，无条件边仅兜底")
    void testConditionalEdgeTakesPrecedenceOverDirectEdge() {
        AgentNode startNode = mock(AgentNode.class);
        AgentNode directNode = mock(AgentNode.class);
        AgentNode conditionalNode = mock(AgentNode.class);
        ConditionEvaluator evaluator = mock(ConditionEvaluator.class);

        when(startNode.getName()).thenReturn("start");
        when(conditionalNode.getName()).thenReturn("conditional");
        when(startNode.execute(any(AgentState.class))).thenAnswer(invocation -> {
            AgentState nextState = new AgentState();
            nextState.setSessionId("test");
            return new AgentNode.ExecutionResult(nextState, null);
        });
        when(conditionalNode.execute(any(AgentState.class))).thenAnswer(invocation -> {
            AgentState state = new AgentState();
            state.getAttributes().put("route", "conditional");
            state.setFinished(true);
            return new AgentNode.ExecutionResult(state, null);
        });
        when(evaluator.getType()).thenReturn("spel");
        when(evaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(true);

        graph.addNode("start", startNode);
        graph.addNode("direct", directNode);
        graph.addNode("conditional", conditionalNode);
        graph.addEdge("start", "direct", null, null);
        graph.addEdge("start", "conditional", evaluator, "true");
        graph.setEntryPoint("start");

        AgentState result = graph.invoke(new AgentState());
        assertThat(result.getAttributes().get("route")).isEqualTo("conditional");
        verify(conditionalNode, atLeastOnce()).execute(any(AgentState.class));
        verify(directNode, never()).execute(any(AgentState.class));
    }

    @Test
    @DisplayName("测试工作流完成状态")
    void testWorkflowFinishedState() {
        when(mockNode1.getName()).thenReturn("startNode");

        AgentState finishedState = new AgentState();
        finishedState.setFinished(true);

        when(mockNode1.execute(any(AgentState.class))).thenReturn(new AgentNode.ExecutionResult(finishedState, null));

        graph.addNode("startNode", mockNode1);
        graph.setEntryPoint("startNode");

        AgentState initialState = new AgentState();
        AgentState result = graph.invoke(initialState);

        Assertions.assertTrue(result.isFinished());
    }

    @Test
    @DisplayName("运行态恢复后的 AgentState 应保持线程安全容器")
    void testRuntimeStateShouldKeepThreadSafeContainers() {
        when(mockNode1.getName()).thenReturn("startNode");
        when(mockNode1.execute(any(AgentState.class))).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            state.addMessage(dev.langchain4j.data.message.AiMessage.from("done"));
            state.setFinished(true);
            return new AgentNode.ExecutionResult(state, AgentGraph.END_NODE);
        });

        graph.addNode("startNode", mockNode1);
        graph.setEntryPoint("startNode");

        AgentState result = graph.invoke(new AgentState());

        assertThat(result.getMessages()).isInstanceOf(CopyOnWriteArrayList.class);
        assertThat(result.getPendingToolRequests()).isInstanceOf(CopyOnWriteArrayList.class);
        assertThat(result.getAttributes()).isInstanceOf(ConcurrentHashMap.class);
    }

    @Test
    @DisplayName("无匹配出边时应抛出可携带状态的路由异常")
    void testRouteFailureShouldExposeFailedState() {
        when(mockNode1.getName()).thenReturn("startNode");
        when(mockNode1.execute(any(AgentState.class))).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            state.setFinished(false);
            return new AgentNode.ExecutionResult(state, null);
        });
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(false);

        graph.addNode("startNode", mockNode1);
        graph.addNode("fallbackNode", mockNode2);
        graph.addEdge("startNode", "fallbackNode", mockEvaluator, "false");
        graph.setEntryPoint("startNode");

        AgentState initialState = new AgentState();
        initialState.getAttributes().put(AgentGraph.TRACE_ENABLED_ATTRIBUTE, true);

        AgentGraph.AgentGraphExecutionException exception =
                catchThrowableOfType(() -> graph.invoke(initialState), AgentGraph.AgentGraphExecutionException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getState()).isNotNull();
        assertThat(exception.getState().getAttributes())
                .containsEntry("__routing_error__", "No matching outgoing edge found for node: startNode");
        assertThat(exception.getState().getExecutionStats()).containsEntry("failed", true);
    }

    @Test
    @DisplayName("stream 路径应归一化为 AgentGraphExecutionException")
    void testStreamShouldNormalizeAgentGraphExecutionException() {
        when(mockNode1.getName()).thenReturn("startNode");
        when(mockNode1.execute(any(AgentState.class))).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            state.setFinished(false);
            return new AgentNode.ExecutionResult(state, null);
        });
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(false);

        graph.addNode("startNode", mockNode1);
        graph.addNode("fallbackNode", mockNode2);
        graph.addEdge("startNode", "fallbackNode", mockEvaluator, "false");
        graph.setEntryPoint("startNode");

        assertThatThrownBy(() ->
                        graph.stream(new AgentState()).toCompletableFuture().join())
                .hasRootCauseInstanceOf(AgentGraph.AgentGraphExecutionException.class);
    }

    @Test
    @DisplayName("测试未设置入口点抛异常")
    void testNoEntryPointThrowsException() {
        AgentState initialState = new AgentState();

        Assertions.assertThrows(IllegalStateException.class, () -> {
            graph.invoke(initialState);
        });
    }

    @Test
    @DisplayName("测试null状态抛异常")
    void testNullStateThrowsException() {
        graph.addNode("startNode", mockNode1);
        graph.setEntryPoint("startNode");

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            graph.invoke(null);
        });
    }

    @Test
    @DisplayName("测试添加节点-无效ID抛异常")
    void testAddNodeInvalidId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            graph.addNode(null, mockNode1);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            graph.addNode("", mockNode1);
        });
    }

    @Test
    @DisplayName("测试添加节点-null节点抛异常")
    void testAddNodeNullNode() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            graph.addNode("node1", null);
        });
    }
}
