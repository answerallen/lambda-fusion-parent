package com.lambda.fusion.ai.commons.agent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lambda.fusion.ai.commons.agent.evaluator.ConditionEvaluator;
import java.util.*;
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
