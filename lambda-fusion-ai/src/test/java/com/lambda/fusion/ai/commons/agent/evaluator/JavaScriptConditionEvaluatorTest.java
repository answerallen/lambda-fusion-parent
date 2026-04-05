package com.lambda.fusion.ai.commons.agent.evaluator;

import static com.lambda.cloud.test.assertion.LambdaAssertions.*;

import com.lambda.fusion.ai.commons.agent.AgentState;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;

class JavaScriptConditionEvaluatorTest {

    private JavaScriptConditionEvaluator evaluator;
    private AgentState state;

    @BeforeEach
    void setUp() {
        evaluator = new JavaScriptConditionEvaluator();
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
    @DisplayName("测试简单布尔表达式")
    void testSimpleBooleanExpression() {
        assertThat(evaluator.evaluate("true", state)).isTrue();
        assertThat(evaluator.evaluate("false", state)).isFalse();
    }

    @Test
    @DisplayName("测试数值比较")
    void testNumericComparison() {
        state.getAttributes().put("value", 10);

        assertThat(evaluator.evaluate("value > 5", state)).isTrue();
        assertThat(evaluator.evaluate("value < 20", state)).isTrue();
        assertThat(evaluator.evaluate("value == 10", state)).isTrue();
        assertThat(evaluator.evaluate("value >= 10", state)).isTrue();
        assertThat(evaluator.evaluate("value <= 10", state)).isTrue();
    }

    @Test
    @DisplayName("测试字符串比较")
    void testStringComparison() {
        state.getAttributes().put("name", "test");

        assertThat(evaluator.evaluate("name == 'test'", state)).isTrue();
        assertThat(evaluator.evaluate("name != 'other'", state)).isTrue();
    }

    @Test
    @DisplayName("测试逻辑运算符")
    void testLogicalOperators() {
        state.getAttributes().put("a", true);
        state.getAttributes().put("b", false);

        assertThat(evaluator.evaluate("a && !b", state)).isTrue();
        assertThat(evaluator.evaluate("a || b", state)).isTrue();
        assertThat(evaluator.evaluate("!b", state)).isTrue();
    }

    @Test
    @DisplayName("测试数学运算")
    void testMathOperations() {
        state.getAttributes().put("x", 5);
        state.getAttributes().put("y", 3);

        assertThat(evaluator.evaluate("x + y == 8", state)).isTrue();
        assertThat(evaluator.evaluate("x - y == 2", state)).isTrue();
        assertThat(evaluator.evaluate("x * y == 15", state)).isTrue();
        assertThat(evaluator.evaluate("x / y > 1", state)).isTrue();
    }

    @Test
    @DisplayName("测试三元运算符")
    void testTernaryOperator() {
        state.getAttributes().put("score", 85);

        assertThat(evaluator.evaluate("score >= 60 ? true : false", state)).isTrue();
    }

    @Test
    @DisplayName("测试数组操作")
    void testArrayOperations() {
        state.getAttributes().put("items", Arrays.asList(1, 2, 3, 4, 5));

        assertThat(evaluator.evaluate("items.length == 5", state)).isTrue();
        assertThat(evaluator.evaluate("items[0] == 1", state)).isTrue();
    }

    @Test
    @DisplayName("测试对象属性访问")
    void testObjectPropertyAccess() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("inner", Map.of("value", 42));
        state.getAttributes().put("nested", nested);

        assertThat(evaluator.evaluate("nested.inner.value == 42", state)).isTrue();
    }

    @Test
    @DisplayName("测试安全-拒绝java.lang.Runtime")
    void testSecurityRejectsRuntime() {
        assertThat(evaluator.evaluate("java.lang.Runtime.getRuntime()", state)).isFalse();
    }

    @Test
    @DisplayName("测试安全-拒绝ProcessBuilder")
    void testSecurityRejectsProcessBuilder() {
        assertThat(evaluator.evaluate("java.lang.ProcessBuilder", state)).isFalse();
    }

    @Test
    @DisplayName("测试安全-拒绝反射")
    void testSecurityRejectsReflection() {
        assertThat(evaluator.evaluate("Class.forName", state)).isFalse();
    }

    @Test
    @DisplayName("测试安全-拒绝文件操作")
    void testSecurityRejectsFileOperations() {
        assertThat(evaluator.evaluate("java.io.File", state)).isFalse();
    }

    @Test
    @DisplayName("测试安全-拒绝eval")
    void testSecurityRejectsEval() {
        assertThat(evaluator.evaluate("eval('test')", state)).isFalse();
    }

    @Test
    @DisplayName("测试安全-拒绝require")
    void testSecurityRejectsRequire() {
        assertThat(evaluator.evaluate("require('fs')", state)).isFalse();
    }

    @Test
    @DisplayName("测试getType返回值")
    void testGetType() {
        assertThat(evaluator.getType()).isEqualTo("js");
    }

    @Test
    @DisplayName("测试异常处理")
    void testExceptionHandling() {
        assertThat(evaluator.evaluate("invalid syntax {{{", state)).isFalse();
    }

    @Test
    @DisplayName("测试null属性值")
    void testNullAttributeValue() {
        state.getAttributes().put("definedValue", "test");

        assertThat(evaluator.evaluate("definedValue != null", state)).isTrue();
    }

    @Test
    @DisplayName("测试未定义变量")
    void testUndefinedVariable() {
        assertThat(evaluator.evaluate("typeof undefinedVar === 'undefined'", state))
                .isTrue();
    }

    @Test
    @DisplayName("测试复杂表达式")
    void testComplexExpression() {
        state.getAttributes()
                .put(
                        "users",
                        Arrays.asList(
                                Map.of("name", "Alice", "age", 30),
                                Map.of("name", "Bob", "age", 25),
                                Map.of("name", "Charlie", "age", 35)));

        assertThat(evaluator.evaluate("users.length == 3", state)).isTrue();
        assertThat(evaluator.evaluate("users[0].name == 'Alice'", state)).isTrue();
    }

    @Test
    @DisplayName("测试JSON操作")
    void testJsonOperations() {
        state.getAttributes().put("jsonStr", "{\"key\":\"value\"}");

        assertThat(evaluator.evaluate("JSON.parse(jsonStr).key == 'value'", state))
                .isTrue();
    }

    @Test
    @DisplayName("测试表达式长度限制")
    void testExpressionLengthLimit() {
        StringBuilder longExpr = new StringBuilder();
        longExpr.repeat("a", 1100);

        assertThat(evaluator.evaluate(longExpr.toString(), state)).isFalse();
    }

    @Test
    @DisplayName("测试state绑定")
    void testStateBinding() {
        state.setSessionId(123L);

        assertThat(evaluator.evaluate("state.getSessionId() == 123", state)).isTrue();
    }

    @Test
    @DisplayName("测试attributes绑定")
    void testAttributesBinding() {
        state.getAttributes().put("testKey", "testValue");

        assertThat(evaluator.evaluate("attributes.testKey == 'testValue'", state))
                .isTrue();
        assertThat(evaluator.evaluate("attrs.testKey == 'testValue'", state)).isTrue();
    }
}
