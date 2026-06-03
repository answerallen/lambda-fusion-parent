package com.lambda.fusion.ai.commons.agent;

import static org.assertj.core.api.Assertions.*;

import com.lambda.fusion.ai.agent.AgentGraph;
import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.AgentState;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import org.junit.jupiter.api.*;

class AgentStateTest {

    private AgentState state;

    @BeforeEach
    void setUp() {
        state = new AgentState();
    }

    @Test
    @DisplayName("测试初始状态-messages默认初始化")
    void testInitialStateMessages() {
        assertThat(state.getMessages()).isNotNull();
        assertThat(state.getMessages()).isEmpty();
        assertThat(state.getMessages()).isInstanceOf(CopyOnWriteArrayList.class);
    }

    @Test
    @DisplayName("测试初始状态-pendingToolRequests默认初始化")
    void testInitialStatePendingToolRequests() {
        assertThat(state.getPendingToolRequests()).isNotNull();
        assertThat(state.getPendingToolRequests()).isEmpty();
        assertThat(state.getPendingToolRequests()).isInstanceOf(CopyOnWriteArrayList.class);
    }

    @Test
    @DisplayName("测试初始状态-attributes默认初始化")
    void testInitialStateAttributes() {
        assertThat(state.getAttributes()).isNotNull();
        assertThat(state.getAttributes()).isEmpty();
        assertThat(state.getAttributes()).isInstanceOf(ConcurrentHashMap.class);
    }

    @Test
    @DisplayName("测试初始状态-finished默认为false")
    void testInitialStateFinished() {
        assertThat(state.isFinished()).isFalse();
    }

    @Test
    @DisplayName("测试初始状态-sessionId默认为null")
    void testInitialStateSessionId() {
        assertThat(state.getSessionId()).isNull();
    }

    @Test
    @DisplayName("测试初始状态-kbId默认为null")
    void testInitialStateKbId() {
        assertThat(state.getKbId()).isNull();
    }

    @Test
    @DisplayName("测试初始状态-llmModelId默认为null")
    void testInitialStateLlmModelId() {
        assertThat(state.getLlmModelId()).isNull();
    }

    @Test
    @DisplayName("测试设置和获取sessionId")
    void testSetAndGetSessionId() {
        state.setSessionId("123");
        assertThat(state.getSessionId()).isEqualTo("123");
    }

    @Test
    @DisplayName("测试设置和获取kbId")
    void testSetAndGetKbId() {
        state.setKbId("1");
        assertThat(state.getKbId()).isEqualTo("1");
    }

    @Test
    @DisplayName("测试设置和获取llmModelId")
    void testSetAndGetLlmModelId() {
        state.setLlmModelId("2");
        assertThat(state.getLlmModelId()).isEqualTo("2");
    }

    @Test
    @DisplayName("测试设置和获取finished")
    void testSetAndGetFinished() {
        state.setFinished(true);
        assertThat(state.isFinished()).isTrue();
    }

    @Test
    @DisplayName("测试添加消息")
    void testAddMessage() {
        UserMessage userMessage = UserMessage.from("Hello");
        AiMessage aiMessage = AiMessage.from("Hi there");

        state.addMessage(userMessage);
        state.addMessage(aiMessage);

        assertThat(state.getMessages()).hasSize(2);
        assertThat(state.getMessages().get(0)).isEqualTo(userMessage);
        assertThat(state.getMessages().get(1)).isEqualTo(aiMessage);
    }

    @Test
    @DisplayName("测试批量添加消息")
    void testAddMessages() {
        List<ChatMessage> messages = Arrays.asList(UserMessage.from("Hello"), UserMessage.from("World"));

        state.addMessages(messages);

        assertThat(state.getMessages()).hasSize(2);
    }

    @Test
    @DisplayName("测试添加工具请求")
    void testAddToolRequest() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("testTool")
                .arguments("{\"arg1\": \"value1\"}")
                .build();

        state.getPendingToolRequests().add(request);

        assertThat(state.getPendingToolRequests()).hasSize(1);
        assertThat(state.getPendingToolRequests().getFirst().name()).isEqualTo("testTool");
    }

    @Test
    @DisplayName("测试设置attributes")
    void testSetAttributes() {
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put("key1", "value1");
        attrs.put("key2", 123);

        state.setAttributes(attrs);

        assertThat(state.getAttributes().get("key1")).isEqualTo("value1");
        assertThat(state.getAttributes().get("key2")).isEqualTo(123);
    }

    @Test
    @DisplayName("测试getCurrentNodeId-从attributes获取")
    void testGetCurrentNodeId() {
        assertThat(state.getCurrentNodeId()).isNull();

        state.getAttributes().put(AgentGraph.CURRENT_NODE_ID_ATTRIBUTE, "node1");

        assertThat(state.getCurrentNodeId()).isEqualTo("node1");
    }

    @Test
    @DisplayName("测试getCurrentNodeType-从attributes获取")
    void testGetCurrentNodeType() {
        assertThat(state.getCurrentNodeType()).isNull();

        state.getAttributes().put(AgentGraph.CURRENT_NODE_TYPE_ATTRIBUTE, "LlmNode");

        assertThat(state.getCurrentNodeType()).isEqualTo("LlmNode");
    }

    @Test
    @DisplayName("测试getCurrentNodeProperties-从attributes获取")
    void testGetCurrentNodeProperties() {
        assertThat(state.getCurrentNodeProperties()).isEmpty();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("temperature", 0.7);
        state.getAttributes().put(AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE, props);

        assertThat(state.getCurrentNodeProperties()).containsEntry("temperature", 0.7);
    }

    @Test
    @DisplayName("测试getGraphNodeProperties-从attributes获取")
    void testGetGraphNodeProperties() {
        assertThat(state.getGraphNodeProperties()).isEmpty();

        Map<String, Map<String, Object>> graphProps = new LinkedHashMap<>();
        graphProps.put("node1", Map.of("prop1", "value1"));
        state.getAttributes().put(AgentGraph.GRAPH_NODE_PROPERTIES_ATTRIBUTE, graphProps);

        assertThat(state.getGraphNodeProperties()).containsKey("node1");
    }

    @Test
    @DisplayName("测试setNodeExecutor和getNodeExecutor")
    void testSetAndGetNodeExecutor() {
        BiFunction<String, AgentState, AgentState> executor = (nodeId, s) -> {
            s.getAttributes().put("executedNode", nodeId);
            return s;
        };

        state.setNodeExecutor(executor);

        assertThat(state.getNodeExecutor()).isNotNull();
        assertThat(state.getNodeExecutor()).isEqualTo(executor);
    }

    @Test
    @DisplayName("测试setAvailableNodes和getAvailableNodes")
    void testSetAndGetAvailableNodes() {
        assertThat(state.getAvailableNodes()).isEmpty();

        Map<String, AgentNode> nodes = new ConcurrentHashMap<>();
        state.setAvailableNodes(nodes);

        assertThat(state.getAvailableNodes()).isNotNull();
    }

    @Test
    @DisplayName("测试getExecutionTrace-默认为空")
    void testGetExecutionTrace() {
        assertThat(state.getExecutionTrace()).isEmpty();
    }

    @Test
    @DisplayName("测试getExecutionStats-默认为空")
    void testGetExecutionStats() {
        assertThat(state.getExecutionStats()).isEmpty();
    }

    @Test
    @DisplayName("测试线程安全性-并发添加消息")
    void testThreadSafetyConcurrentMessages() throws InterruptedException {
        int threadCount = 10;
        int messagesPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    state.addMessage(UserMessage.from("Thread-" + threadIndex + "-Msg-" + j));
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(state.getMessages()).hasSize(threadCount * messagesPerThread);
    }

    @Test
    @DisplayName("测试线程安全性-并发属性操作")
    void testThreadSafetyConcurrentAttributes() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = "key-" + threadIndex + "-" + j;
                    state.getAttributes().put(key, threadIndex * 1000 + j);
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(state.getAttributes()).hasSize(threadCount * operationsPerThread);
    }
}
