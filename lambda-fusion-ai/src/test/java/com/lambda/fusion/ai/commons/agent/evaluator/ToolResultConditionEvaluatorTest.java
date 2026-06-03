package com.lambda.fusion.ai.commons.agent.evaluator;

import static com.lambda.cloud.test.assertion.LambdaAssertions.*;

import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.evaluator.ToolResultConditionEvaluator;
import java.util.*;
import org.junit.jupiter.api.*;

class ToolResultConditionEvaluatorTest {

    private ToolResultConditionEvaluator evaluator;
    private AgentState state;

    @BeforeEach
    void setUp() {
        evaluator = new ToolResultConditionEvaluator();
        state = new AgentState();
    }

    @Test
    @DisplayName("测试空表达式返回true")
    void testEmptyExpression() {
        assertThat(evaluator.evaluate(null, state)).isTrue();
        assertThat(evaluator.evaluate("", state)).isTrue();
        assertThat(evaluator.evaluate("   ", state)).isTrue();
    }

    @Test
    @DisplayName("测试toolSuccess表达式")
    void testToolSuccessExpression() {
        Map<String, Object> lastResult = new HashMap<>();
        lastResult.put("success", true);
        state.getAttributes().put("lastToolResult", lastResult);

        assertThat(evaluator.evaluate("toolSuccess", state)).isTrue();
        assertThat(evaluator.evaluate("success", state)).isTrue();
    }

    @Test
    @DisplayName("测试toolFailed表达式")
    void testToolFailedExpression() {
        Map<String, Object> lastResult = new HashMap<>();
        lastResult.put("success", false);
        state.getAttributes().put("lastToolResult", lastResult);

        assertThat(evaluator.evaluate("toolFailed", state)).isTrue();
        assertThat(evaluator.evaluate("failed", state)).isTrue();
    }

    @Test
    @DisplayName("测试hasResult表达式")
    void testHasResultExpression() {
        assertThat(evaluator.evaluate("hasResult", state)).isFalse();

        Map<String, Object> toolResults = new HashMap<>();
        toolResults.put("tool1", Map.of("result", "success"));
        state.getAttributes().put("toolResults", toolResults);

        assertThat(evaluator.evaluate("hasResult", state)).isTrue();
        assertThat(evaluator.evaluate("hasResults", state)).isTrue();
    }

    @Test
    @DisplayName("测试hasPendingTools表达式")
    void testHasPendingToolsExpression() {
        assertThat(evaluator.evaluate("hasPendingTools", state)).isFalse();

        state.getPendingToolRequests()
                .add(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                        .name("testTool")
                        .arguments("{}")
                        .build());

        assertThat(evaluator.evaluate("hasPendingTools", state)).isTrue();
    }

    @Test
    @DisplayName("测试resultCount表达式")
    void testResultCountExpression() {
        Map<String, Object> toolResults = new HashMap<>();
        toolResults.put("tool1", Map.of("result", "success"));
        toolResults.put("tool2", Map.of("result", "success"));
        toolResults.put("tool3", Map.of("result", "success"));
        state.getAttributes().put("toolResults", toolResults);

        assertThat(evaluator.evaluate("resultCount >= 3", state)).isTrue();
        assertThat(evaluator.evaluate("resultCount > 2", state)).isTrue();
        assertThat(evaluator.evaluate("resultCount == 3", state)).isTrue();
        assertThat(evaluator.evaluate("resultCount < 5", state)).isTrue();
    }

    @Test
    @DisplayName("测试result属性表达式")
    void testResultPropertyExpression() {
        Map<String, Object> lastResult = new HashMap<>();
        lastResult.put("status", "success");
        lastResult.put("code", 200);
        lastResult.put("message", "OK");
        state.getAttributes().put("lastToolResult", lastResult);

        assertThat(evaluator.evaluate("result.status == 'success'", state)).isTrue();
        assertThat(evaluator.evaluate("result.code == 200", state)).isTrue();
        assertThat(evaluator.evaluate("result.message == 'OK'", state)).isTrue();
    }

    @Test
    @DisplayName("测试contains表达式")
    void testContainsExpression() {
        Map<String, Object> lastResult = new HashMap<>();
        lastResult.put("data", "This is a test message with error info");
        state.getAttributes().put("lastToolResult", lastResult);

        assertThat(evaluator.evaluate("result.contains('test')", state)).isTrue();
        assertThat(evaluator.evaluate("result.contains('error')", state)).isTrue();
        assertThat(evaluator.evaluate("result.data.contains('message')", state)).isTrue();
    }

    @Test
    @DisplayName("测试状态码判断")
    void testStatusCodeJudgment() {
        Map<String, Object> lastResult = new HashMap<>();
        lastResult.put("code", 200);
        state.getAttributes().put("lastToolResult", lastResult);

        assertThat(evaluator.evaluate("toolSuccess", state)).isTrue();

        lastResult.put("code", 500);
        assertThat(evaluator.evaluate("toolFailed", state)).isTrue();
    }

    @Test
    @DisplayName("测试status字段判断")
    void testStatusFieldJudgment() {
        Map<String, Object> lastResult = new HashMap<>();
        lastResult.put("status", "success");
        state.getAttributes().put("lastToolResult", lastResult);

        assertThat(evaluator.evaluate("toolSuccess", state)).isTrue();

        lastResult.put("status", "error");
        assertThat(evaluator.evaluate("toolFailed", state)).isTrue();
    }

    @Test
    @DisplayName("测试无错误信息视为成功")
    void testNoErrorMeansSuccess() {
        Map<String, Object> lastResult = new HashMap<>();
        lastResult.put("data", "some data");
        state.getAttributes().put("lastToolResult", lastResult);

        assertThat(evaluator.evaluate("toolSuccess", state)).isTrue();
    }

    @Test
    @DisplayName("测试有错误信息视为失败")
    void testErrorFieldMeansFailed() {
        Map<String, Object> lastResult = new HashMap<>();
        lastResult.put("error", "Something went wrong");
        state.getAttributes().put("lastToolResult", lastResult);

        assertThat(evaluator.evaluate("toolFailed", state)).isTrue();
    }

    @Test
    @DisplayName("测试无lastToolResult时从toolResults获取")
    void testGetLastResultFromToolResults() {
        Map<String, Object> toolResults = new HashMap<>();
        toolResults.put("tool1", Map.of("success", true));
        state.getAttributes().put("toolResults", toolResults);

        assertThat(evaluator.evaluate("toolSuccess", state)).isTrue();
    }

    @Test
    @DisplayName("测试getType返回值")
    void testGetType() {
        assertThat(evaluator.getType()).isEqualTo("tool");
    }
}
