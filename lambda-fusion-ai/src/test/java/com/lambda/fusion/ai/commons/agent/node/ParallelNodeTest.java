package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.utils.AgentUtils.setCurrentNodeProperties;
import static org.assertj.core.api.Assertions.*;

import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.node.ParallelNode;
import com.lambda.fusion.ai.utils.AgentUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;

class ParallelNodeTest {

    private ParallelNode parallelNode;

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
        setCurrentNodeProperties(state, properties);

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
        setCurrentNodeProperties(state, properties);

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

        setCurrentNodeProperties(state, properties);

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

        setCurrentNodeProperties(state, properties);

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
        setCurrentNodeProperties(state, properties);

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
        setCurrentNodeProperties(state, properties);

        assertThatCode(() -> parallelNode.execute(state)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("测试并行上下文保存")
    void testParallelContextSave() {
        Map<String, Object> properties = getStringObjectMap();

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);
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

        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = parallelNode.execute(state);

        assertThat(result.nextNode()).isEqualTo("finalNode");
    }

    @Test
    @DisplayName("并行结果仅保留分支新增消息")
    void shouldOnlyKeepDeltaMessagesInParallelResults() {
        Map<String, String> branch1 = new HashMap<>();
        branch1.put("id", "branch1");
        branch1.put("target", "node1");
        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", List.of(branch1));
        properties.put("joinNode", "joinNode");

        AgentState state = new AgentState();
        state.addMessage(UserMessage.from("父消息"));
        setCurrentNodeProperties(state, properties);
        state.setNodeExecutor((nodeId, input) -> {
            AgentState output = new AgentState();
            output.setSessionId(input.getSessionId());
            output.setKbId(input.getKbId());
            output.setLlmModelId(input.getLlmModelId());
            output.setAttributes(new HashMap<>(input.getAttributes()));
            output.setMessages(new ArrayList<>(input.getMessages()));
            output.addMessage(AiMessage.from("分支新增消息"));
            return output;
        });

        AgentNode.ExecutionResult result = parallelNode.execute(state);

        assertThat(result.nextNode()).isEqualTo("joinNode");
        @SuppressWarnings("unchecked")
        Map<String, Object> parallelResults =
                (Map<String, Object>) state.getAttributes().get("__parallel_results__");
        assertThat(parallelResults).containsKey("branch1");
        @SuppressWarnings("unchecked")
        Map<String, Object> branchResult = (Map<String, Object>) parallelResults.get("branch1");
        assertThat((List<?>) branchResult.get("messages")).hasSize(1);
    }

    @Test
    @DisplayName("主动取消的分支不应被记录为失败")
    void shouldNotTreatCancelledBranchAsFailure() {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        ParallelNode asyncParallelNode = new ParallelNode(executorService);
        try {
            Map<String, Object> properties = getParallelPropertiesForCancellation();

            AgentState state = new AgentState();
            setCurrentNodeProperties(state, properties);
            state.setNodeExecutor((nodeId, input) -> {
                if ("fastNode".equals(nodeId)) {
                    AgentState output = AgentUtils.deepCopyState(input);
                    output.addMessage(AiMessage.from("fast"));
                    return output;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return input;
            });

            AgentNode.ExecutionResult result = asyncParallelNode.execute(state);

            assertThat(result.nextNode()).isEqualTo("joinNode");
            assertThat(state.getAttributes()).doesNotContainKey("__parallel_errors__");
            @SuppressWarnings("unchecked")
            Map<String, Object> parallelResults =
                    (Map<String, Object>) state.getAttributes().get("__parallel_results__");
            @SuppressWarnings("unchecked")
            Map<String, Object> cancelledBranch = (Map<String, Object>) parallelResults.get("slowBranch");
            assertThat(cancelledBranch).containsEntry("cancelled", true);
            assertThat(cancelledBranch).containsEntry("success", false);
        } finally {
            executorService.shutdownNow();
        }
    }

    private static Map<String, Object> getParallelPropertiesForCancellation() {
        Map<String, String> fastBranch = new HashMap<>();
        fastBranch.put("id", "fastBranch");
        fastBranch.put("target", "fastNode");
        Map<String, String> slowBranch = new HashMap<>();
        slowBranch.put("id", "slowBranch");
        slowBranch.put("target", "slowNode");

        Map<String, Object> properties = new HashMap<>();
        properties.put("branches", List.of(fastBranch, slowBranch));
        properties.put("joinNode", "joinNode");
        properties.put("waitAll", false);
        properties.put("timeout", 3000);
        return properties;
    }
}
