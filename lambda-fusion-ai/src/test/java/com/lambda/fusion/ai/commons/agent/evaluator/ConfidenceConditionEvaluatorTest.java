package com.lambda.fusion.ai.commons.agent.evaluator;

import static org.assertj.core.api.Assertions.*;

import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.evaluator.ConfidenceConditionEvaluator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;

class ConfidenceConditionEvaluatorTest {

    private ConfidenceConditionEvaluator evaluator;
    private AgentState state;

    @BeforeEach
    void setUp() {
        evaluator = new ConfidenceConditionEvaluator();
        state = new AgentState();
        state.setAttributes(new ConcurrentHashMap<>());
    }

    @Test
    @DisplayName("测试空表达式返回true")
    void testEmptyExpression() {
        assertThat(evaluator.evaluate(null, state)).isTrue();
        assertThat(evaluator.evaluate("", state)).isTrue();
        assertThat(evaluator.evaluate("   ", state)).isTrue();
    }

    @Test
    @DisplayName("测试confidence大于阈值")
    void testConfidenceGreaterThanThreshold() {
        state.getAttributes().put("confidence", 0.85);

        assertThat(evaluator.evaluate("confidence > 0.7", state)).isTrue();
        assertThat(evaluator.evaluate("confidence > 0.9", state)).isFalse();
    }

    @Test
    @DisplayName("测试confidence大于等于阈值")
    void testConfidenceGreaterThanOrEqual() {
        state.getAttributes().put("confidence", 0.8);

        assertThat(evaluator.evaluate("confidence >= 0.8", state)).isTrue();
        assertThat(evaluator.evaluate("confidence >= 0.7", state)).isTrue();
        assertThat(evaluator.evaluate("confidence >= 0.9", state)).isFalse();
    }

    @Test
    @DisplayName("测试confidence小于阈值")
    void testConfidenceLessThanThreshold() {
        state.getAttributes().put("confidence", 0.3);

        assertThat(evaluator.evaluate("confidence < 0.5", state)).isTrue();
        assertThat(evaluator.evaluate("confidence < 0.2", state)).isFalse();
    }

    @Test
    @DisplayName("测试confidence小于等于阈值")
    void testConfidenceLessThanOrEqual() {
        state.getAttributes().put("confidence", 0.5);

        assertThat(evaluator.evaluate("confidence <= 0.5", state)).isTrue();
        assertThat(evaluator.evaluate("confidence <= 0.6", state)).isTrue();
        assertThat(evaluator.evaluate("confidence <= 0.4", state)).isFalse();
    }

    @Test
    @DisplayName("测试confidence等于阈值")
    void testConfidenceEqual() {
        state.getAttributes().put("confidence", 0.75);

        assertThat(evaluator.evaluate("confidence == 0.75", state)).isTrue();
        assertThat(evaluator.evaluate("confidence = 0.75", state)).isTrue();
        assertThat(evaluator.evaluate("confidence == 0.8", state)).isFalse();
    }

    @Test
    @DisplayName("测试区间表达式-0.3<=confidence<=0.7")
    void testRangeExpression() {
        state.getAttributes().put("confidence", 0.5);
        assertThat(evaluator.evaluate("0.3 <= confidence <= 0.7", state)).isTrue();

        state.getAttributes().put("confidence", 0.8);
        assertThat(evaluator.evaluate("0.3 <= confidence <= 0.7", state)).isFalse();
    }

    @Test
    @DisplayName("测试反向表达式-0.8>confidence")
    void testReverseExpression() {
        state.getAttributes().put("confidence", 0.3);

        assertThat(evaluator.evaluate("0.8 > confidence", state)).isTrue();
        assertThat(evaluator.evaluate("0.2 > confidence", state)).isFalse();
    }

    @Test
    @DisplayName("测试无confidence属性")
    void testNoConfidenceAttribute() {
        assertThat(evaluator.evaluate("confidence > 0.5", state)).isFalse();
    }

    @Test
    @DisplayName("测试confidence为非数字类型")
    void testConfidenceNonNumeric() {
        state.getAttributes().put("confidence", "high");

        assertThat(evaluator.evaluate("confidence > 0.5", state)).isFalse();
    }

    @Test
    @DisplayName("测试confidence为Integer类型")
    void testConfidenceIntegerType() {
        state.getAttributes().put("confidence", 1);

        assertThat(evaluator.evaluate("confidence > 0.5", state)).isTrue();
    }

    @Test
    @DisplayName("测试confidence为Long类型")
    void testConfidenceLongType() {
        state.getAttributes().put("confidence", 1L);

        assertThat(evaluator.evaluate("confidence > 0.5", state)).isTrue();
    }

    @Test
    @DisplayName("测试confidence为Float类型")
    void testConfidenceFloatType() {
        state.getAttributes().put("confidence", 0.8f);

        assertThat(evaluator.evaluate("confidence > 0.7", state)).isTrue();
    }

    @Test
    @DisplayName("测试getType返回值")
    void testGetType() {
        assertThat(evaluator.getType()).isEqualTo("confidence");
    }

    @Test
    @DisplayName("测试异常处理")
    void testExceptionHandling() {
        state.setAttributes(null);
        assertThat(evaluator.evaluate("confidence > 0.5", state)).isFalse();
    }

    @Test
    @DisplayName("测试边界值-0.0")
    void testBoundaryValueZero() {
        state.getAttributes().put("confidence", 0.0);

        assertThat(evaluator.evaluate("confidence >= 0", state)).isTrue();
        assertThat(evaluator.evaluate("confidence > 0", state)).isFalse();
    }

    @Test
    @DisplayName("测试边界值-1.0")
    void testBoundaryValueOne() {
        state.getAttributes().put("confidence", 1.0);

        assertThat(evaluator.evaluate("confidence <= 1", state)).isTrue();
        assertThat(evaluator.evaluate("confidence < 1", state)).isFalse();
    }

    @Test
    @DisplayName("测试从intent对象提取confidence")
    void testExtractConfidenceFromIntent() {
        Map<String, Object> intent = new HashMap<>();
        intent.put("confidence", 0.85);
        state.getAttributes().put("intent", intent);

        assertThat(evaluator.evaluate("confidence > 0.7", state)).isTrue();
    }

    @Test
    @DisplayName("测试不支持的表达式格式")
    void testUnsupportedExpressionFormat() {
        state.getAttributes().put("confidence", 0.5);

        assertThat(evaluator.evaluate("unknownFormat", state)).isFalse();
    }
}
