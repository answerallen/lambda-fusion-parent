package com.lambda.fusion.ai.commons.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.commons.agent.evaluator.ToolResultConditionEvaluator;
import com.lambda.fusion.ai.commons.agent.node.ParallelNode;
import com.lambda.fusion.ai.commons.agent.node.ToolExecutingNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.StopWatch;

public class AgentGraphIssuesVerificationTest {

    @Test
    @DisplayName("AgentGraph 能正确回写新增消息并记录 messageDelta")
    void verifyMessageLossIssue() {
        AgentGraph graph = new AgentGraph();

        AgentNode llmNodeMock = new AgentNode() {
            @Override
            public String getName() {
                return "LLM_PROCESSOR_MOCK";
            }

            @Override
            public ExecutionResult execute(AgentState state) {
                // 原地修改：节点内部直接在 state 的 list 上 append
                state.addMessage(AiMessage.from("LLM response mock"));
                return new ExecutionResult(state, AgentGraph.END_NODE);
            }
        };

        graph.addNode("llm", llmNodeMock);
        graph.setEntryPoint("llm");

        AgentState initialState = new AgentState();
        initialState.setSessionId("test-session");
        initialState.addMessage(UserMessage.from("Hello"));
        // 开启 Trace 以观察 messageDelta
        initialState.getAttributes().put(AgentGraph.TRACE_ENABLED_ATTRIBUTE, true);

        AgentState finalState = graph.invoke(initialState);

        // 新增 AI 消息应被正确回写，最终为 2 条
        assertThat(finalState.getMessages()).hasSize(2);

        List<Map<String, Object>> trace = finalState.getExecutionTrace();
        Map<String, Object> nodeEvent = trace.stream()
                .filter(e -> "node".equals(e.get("kind")) && "llm".equals(e.get("nodeId")))
                .findFirst()
                .orElseThrow();

        // messageDelta 应正确记录为 1
        assertThat(nodeEvent.get("messageDelta")).isEqualTo(1);
        System.out.println("LLM 节点新增了 1 条消息，finalState 消息数为 "
                + finalState.getMessages().size()
                + "，Trace 记录的新增消息数为: "
                + nodeEvent.get("messageDelta")
                + " (预期均为 +1)");
    }

    @Test
    @DisplayName("ToolExecutingNode 回写结果后可被 ToolResultConditionEvaluator 识别")
    void verifyToolEvaluatorDisconnection() {
        AgentToolProvider providerMock = mock(AgentToolProvider.class);
        when(providerMock.executeTool(any())).thenReturn("Success from tool");

        ToolExecutingNode toolNode = new ToolExecutingNode(providerMock);

        AgentState state = new AgentState();
        state.getAttributes().put(AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE, new HashMap<>());
        state.getPendingToolRequests()
                .add(ToolExecutionRequest.builder()
                        .id("call_123")
                        .name("testTool")
                        .arguments("{}")
                        .build());

        toolNode.execute(state);

        // 验证 ToolNode 的确执行了并添加了消息
        assertThat(state.getMessages()).hasSize(1);
        assertThat(state.getPendingToolRequests()).isEmpty();
        assertThat(state.getAttributes()).containsKeys("lastToolResult", "toolResults");

        // 4. 此时，图引擎流转到条件评估器，用 evaluator 评估
        ToolResultConditionEvaluator evaluator = new ToolResultConditionEvaluator();
        boolean isSuccess = evaluator.evaluate("toolSuccess", state);

        // ToolExecutingNode 会回写 lastToolResult/toolResults，评估器应返回 true
        assertThat(isSuccess).isTrue();
        System.out.println("工具执行成功，evaluator.evaluate(\"toolSuccess\") 返回: " + isSuccess + " (预期为 true)");
    }

    @Test
    @DisplayName("缺少 -parameters 导致参数名丢失")
    void verifyParameterNameLoss() throws Exception {
        Method method = DummyTool.class.getMethod("execute", String.class, int.class);
        Parameter[] parameters = method.getParameters();

        String paramName = parameters[0].getName();
        // 如果没有 -parameters，参数名通常是 arg0。如果有，则是 dummyParam。
        System.out.println("通过反射获取到的参数名: " + paramName);
    }

    static class DummyTool {
        public void execute(String dummyParam, int dummyInt) {}
    }

    @Test
    @DisplayName("ParallelNode failFast 并未立即中断主线程等待")
    void verifyParallelFailFastSemantics() {
        Executor executor = Executors.newFixedThreadPool(2);
        ParallelNode parallelNode = new ParallelNode(executor);

        AgentState state = new AgentState();

        // 模拟执行器：
        // 分支 1：立即抛出异常
        // 分支 2：休眠 1000 毫秒
        state.setNodeExecutor((target, s) -> {
            if ("failNode".equals(target)) {
                throw new RuntimeException("Immediate failure");
            }
            if ("sleepNode".equals(target)) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return s;
        });

        Map<String, Object> props = new HashMap<>();
        List<Map<String, String>> branches = new ArrayList<>();
        branches.add(Map.of("id", "1", "target", "failNode"));
        branches.add(Map.of("id", "2", "target", "sleepNode"));
        props.put("branches", branches);
        props.put("errorStrategy", "failFast");
        props.put("waitAll", true);
        props.put("timeout", 5000); // 长超时
        props.put("joinNode", "end");

        state.getAttributes().put(AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE, props);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        parallelNode.execute(state);

        stopWatch.stop();
        long duration = stopWatch.getTotalTimeMillis();

        System.out.println("failFast 模式下包含一个立即失败分支和一个 1 秒休眠分支，总耗时: " + duration + " ms");
        // 虽然配置了 failFast，但主线程因为 waitAll=true 依然等待了 1000ms
        assertThat(duration).isGreaterThanOrEqualTo(900);
    }
}
