package com.lambda.fusion.ai.commons.agent.evaluator;

import static com.lambda.cloud.test.assertion.LambdaAssertions.*;

import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentState;
import java.util.*;
import org.junit.jupiter.api.*;

class SpelConditionEvaluatorTest {

    private SpelConditionEvaluator evaluator;
    private AgentState state;

    @BeforeEach
    void setUp() {
        evaluator = new SpelConditionEvaluator();
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
    @DisplayName("测试简单布尔表达式")
    void testSimpleBooleanExpression() {
        state.setFinished(true);
        assertThat(evaluator.evaluate("#state.finished == true", state)).isTrue();
        assertThat(evaluator.evaluate("#state.finished", state)).isTrue();
    }

    @Test
    @DisplayName("测试访问状态属性")
    void testAccessStateProperties() {
        state.setSessionId("123");
        state.setKbId("1");
        state.setLlmModelId("2");

        assertThat(evaluator.evaluate("#state.sessionId == '123'", state)).isTrue();
        assertThat(evaluator.evaluate("#state.kbId == '1'", state)).isTrue();
        assertThat(evaluator.evaluate("#state.llmModelId == '2'", state)).isTrue();
    }

    @Test
    @DisplayName("测试访问attributes")
    void testAccessAttributes() {
        state.getAttributes().put("name", "test");
        state.getAttributes().put("count", 10);
        state.getAttributes().put("active", true);

        assertThat(evaluator.evaluate("#name == 'test'", state)).isTrue();
        assertThat(evaluator.evaluate("#count > 5", state)).isTrue();
        assertThat(evaluator.evaluate("#active == true", state)).isTrue();
    }

    @Test
    @DisplayName("测试访问currentNodeProperties")
    void testAccessCurrentNodeProperties() {
        Map<String, Object> nodeProps = new HashMap<>();
        nodeProps.put("temperature", 0.7);
        nodeProps.put("maxTokens", 2000);
        state.getAttributes().put(AgentGraph.CURRENT_NODE_PROPERTIES_ATTRIBUTE, nodeProps);

        assertThat(evaluator.evaluate("#nodeProps['temperature'] > 0.5", state)).isTrue();
        assertThat(evaluator.evaluate("#currentNodeProperties['maxTokens'] >= 2000", state))
                .isTrue();
    }

    @Test
    @DisplayName("测试hasTools变量")
    void testHasToolsVariable() {
        assertThat(evaluator.evaluate("#hasTools == false", state)).isTrue();

        state.getPendingToolRequests()
                .add(dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                        .name("testTool")
                        .arguments("{}")
                        .build());

        assertThat(evaluator.evaluate("#hasTools == true", state)).isTrue();
    }

    @Test
    @DisplayName("测试安全-拒绝危险表达式-T()")
    void testSecurityRejectsT() {
        assertThat(evaluator.evaluate("T(java.lang.Runtime).getRuntime()", state))
                .isFalse();
    }

    @Test
    @DisplayName("测试安全-拒绝危险表达式-new")
    void testSecurityRejectsNew() {
        assertThat(evaluator.evaluate("new java.lang.ProcessBuilder", state)).isFalse();
    }

    @Test
    @DisplayName("测试安全-拒绝危险表达式-runtime")
    void testSecurityRejectsRuntime() {
        assertThat(evaluator.evaluate("runtime.exec('cmd')", state)).isFalse();
    }

    @Test
    @DisplayName("测试安全-拒绝危险表达式-class")
    void testSecurityRejectsClass() {
        assertThat(evaluator.evaluate("#state.class", state)).isFalse();
    }

    @Test
    @DisplayName("测试逻辑运算符")
    void testLogicalOperators() {
        state.getAttributes().put("a", 5);
        state.getAttributes().put("b", 3);

        assertThat(evaluator.evaluate("#a > 3 && #b > 1", state)).isTrue();
        assertThat(evaluator.evaluate("#a > 10 || #b > 1", state)).isTrue();
        assertThat(evaluator.evaluate("!(#a > 10)", state)).isTrue();
    }

    @Test
    @DisplayName("测试比较运算符")
    void testComparisonOperators() {
        state.getAttributes().put("value", 10);

        assertThat(evaluator.evaluate("#value > 5", state)).isTrue();
        assertThat(evaluator.evaluate("#value >= 10", state)).isTrue();
        assertThat(evaluator.evaluate("#value < 20", state)).isTrue();
        assertThat(evaluator.evaluate("#value <= 10", state)).isTrue();
        assertThat(evaluator.evaluate("#value == 10", state)).isTrue();
        assertThat(evaluator.evaluate("#value != 5", state)).isTrue();
    }

    @Test
    @DisplayName("测试三元运算符")
    void testTernaryOperator() {
        state.getAttributes().put("score", 85);

        assertThat(evaluator.evaluate("#score >= 60 ? true : false", state)).isTrue();
    }

    @Test
    @DisplayName("测试getType返回值")
    void testGetType() {
        assertThat(evaluator.getType()).isEqualTo("spel");
    }
}
