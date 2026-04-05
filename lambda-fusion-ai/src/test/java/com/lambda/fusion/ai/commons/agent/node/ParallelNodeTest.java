package com.lambda.fusion.ai.commons.agent.node;

import static org.assertj.core.api.Assertions.*;

import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentNode;
import com.lambda.fusion.ai.commons.agent.AgentState;
import java.util.*;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;

class ParallelNodeTest {

    private ParallelNode parallelNode;

    private void setNodeProperties(AgentState state, Map<String, Object> properties) {
        state.getAttributes().put(AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE, properties);
    }

    @BeforeEach
    void setUp() {
        parallelNode = new ParallelNode(Runnable::run);
    }

    @Test
    @DisplayName("测试节点名称")
    void testNodeName() {
        assertThat(parallelNode.getName()).isEqualTo("PARALLEL");
    }

    @Test
    @DisplayName("测试空属性返回null")
    void testNullProperties() {
        AgentState state = new AgentState();
        AgentNode.ExecutionResult result = parallelNode.execute(state);

        assertThat(result).isNotNull();
        assertThat(result.nextNode()).isNull();
    }

    @Test
    @DisplayName("测试空分支列表")
    void testEmptyBranches() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", Collections.emptyList());
        properties.put("joinNode", "joinNode");

        AgentState state = new AgentState();
        setNodeProperties(state, properties);

        AgentNode.ExecutionResult result = parallelNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试并行执行上下文初始化")
    void testParallelContextInitialization() {
        List<String> branches = Arrays.asList("branch1", "branch2", "branch3");

        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", branches);
        properties.put("joinNode", "joinNode");
        properties.put("waitAll", true);
        properties.put("timeout", 60000);
        properties.put("errorStrategy", "failFast");

        AgentState state = new AgentState();
        setNodeProperties(state, properties);

        AgentNode.ExecutionResult result = parallelNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试类型安全-timeout从Map恢复")
    void testTypeSafeTimeoutRecovery() {
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("isStarted", true);
        contextMap.put("isCompleted", false);
        contextMap.put("startTime", System.currentTimeMillis());
        contextMap.put("timeout", 30000L);
        contextMap.put("errorStrategy", "failFast");
        contextMap.put("waitAll", Boolean.TRUE);
        contextMap.put("pendingBranches", new HashSet<>(List.of("branch1")));
        contextMap.put("completedBranches", new HashMap<>());
        contextMap.put("failedBranches", new HashMap<>());

        AgentState state = new AgentState();
        state.getAttributes().put("__parallel_context__", contextMap);

        List<String> branches = List.of("branch1");
        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", branches);
        properties.put("joinNode", "joinNode");

        setNodeProperties(state, properties);

        assertThatCode(() -> parallelNode.execute(state)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("测试类型安全-waitAll从Map恢复")
    void testTypeSafeWaitAllRecovery() {
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("isStarted", true);
        contextMap.put("isCompleted", true);
        contextMap.put("startTime", System.currentTimeMillis() - 1000);
        contextMap.put("completedAt", System.currentTimeMillis());
        contextMap.put("timeout", 60000);
        contextMap.put("errorStrategy", "failFast");
        contextMap.put("waitAll", "true");
        contextMap.put("pendingBranches", Collections.emptySet());
        contextMap.put("completedBranches", Map.of("branch1", "result1"));
        contextMap.put("failedBranches", Collections.emptyMap());

        AgentState state = new AgentState();
        state.getAttributes().put("__parallel_context__", contextMap);

        List<String> branches = List.of("branch1");
        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", branches);
        properties.put("joinNode", "joinNode");

        setNodeProperties(state, properties);

        AgentNode.ExecutionResult result = parallelNode.execute(state);

        assertThat(result.nextNode()).isEqualTo("joinNode");
    }

    @Test
    @DisplayName("测试waitAll默认值为true")
    void testWaitAllDefaultValue() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", List.of("branch1"));
        properties.put("joinNode", "joinNode");

        AgentState state = new AgentState();
        setNodeProperties(state, properties);

        assertThatCode(() -> parallelNode.execute(state)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("测试errorStrategy配置")
    void testErrorStrategyConfiguration() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", List.of("branch1"));
        properties.put("joinNode", "joinNode");
        properties.put("errorStrategy", "ignore");
        properties.put("waitAll", false);

        AgentState state = new AgentState();
        setNodeProperties(state, properties);

        assertThatCode(() -> parallelNode.execute(state)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("测试并行上下文保存")
    void testParallelContextSave() {
        Map<String, Object> properties = getStringObjectMap();

        AgentState state = new AgentState();
        setNodeProperties(state, properties);
        // 设置节点执行器，否则并行节点会跳过执行
        state.setNodeExecutor((nodeId, s) -> s);

        AgentNode.ExecutionResult result = parallelNode.execute(state);

        // 验证执行结果不为null，且执行完成（因为使用的是Runnable::run同步执行器）
        assertThat(result).isNotNull();
    }

    private static @NonNull Map<String, Object> getStringObjectMap() {
        Map<String, String> branch1 = new HashMap<>();
        branch1.put("id", "branch1");
        branch1.put("target", "node1");
        Map<String, String> branch2 = new HashMap<>();
        branch2.put("id", "branch2");
        branch2.put("target", "node2");
        List<Map<String, String>> branches = Arrays.asList(branch1, branch2);

        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", branches);
        properties.put("joinNode", "joinNode");
        properties.put("waitAll", true);
        return properties;
    }

    @Test
    @DisplayName("测试完成状态恢复")
    void testCompletedStateRecovery() {
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("isStarted", true);
        contextMap.put("isCompleted", true);
        contextMap.put("startTime", System.currentTimeMillis() - 1000);
        contextMap.put("completedAt", System.currentTimeMillis());
        contextMap.put("timeout", 60000);
        contextMap.put("errorStrategy", "failFast");
        contextMap.put("waitAll", true);
        contextMap.put("pendingBranches", Collections.emptySet());
        contextMap.put("completedBranches", new HashMap<>());
        contextMap.put("failedBranches", Collections.emptyMap());

        AgentState state = new AgentState();
        state.getAttributes().put("__parallel_context__", contextMap);

        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", Collections.emptyList());
        properties.put("joinNode", "finalNode");

        setNodeProperties(state, properties);

        AgentNode.ExecutionResult result = parallelNode.execute(state);

        assertThat(result.nextNode()).isEqualTo("finalNode");
    }
}
