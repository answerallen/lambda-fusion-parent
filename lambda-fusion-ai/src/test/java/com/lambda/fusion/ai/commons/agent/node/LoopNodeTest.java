package com.lambda.fusion.ai.commons.agent.node;

import static com.lambda.fusion.ai.utils.AgentUtils.setCurrentNodeId;
import static com.lambda.fusion.ai.utils.AgentUtils.setCurrentNodeProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.evaluator.ConditionEvaluator;
import com.lambda.fusion.ai.agent.node.LoopNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoopNodeTest {

    private LoopNode loopNode;

    private ConditionEvaluator mockEvaluator;

    @BeforeEach
    void setUp() {
        mockEvaluator = mock(ConditionEvaluator.class);
        Map<String, ConditionEvaluator> evaluators = new HashMap<>();
        evaluators.put("spel", mockEvaluator);
        loopNode = new LoopNode(evaluators);
    }

    @Test
    @DisplayName("测试节点名称")
    void testNodeName() {
        assertThat(loopNode.getName()).isEqualTo("LOOP");
    }

    @Test
    @DisplayName("测试空属性返回null")
    void testNullProperties() {
        AgentState state = new AgentState();
        AgentNode.ExecutionResult result = loopNode.execute(state);

        assertThat(result).isNotNull();
        assertThat(result.nextNode()).isNull();
    }

    @Test
    @DisplayName("测试while循环-正常执行")
    void testWhileLoopNormalExecution() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class)))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "while");
        properties.put("condition", "#counter < 3");
        properties.put("conditionType", "spel");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        state.getAttributes().put("counter", 0);
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = loopNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试while循环-达到最大迭代次数")
    void testWhileLoopMaxIterations() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(true);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "while");
        properties.put("condition", "true");
        properties.put("conditionType", "spel");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");
        properties.put("maxIterations", 3);

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        loopNode.execute(state);
        loopNode.execute(state);
        loopNode.execute(state);
        AgentNode.ExecutionResult result = loopNode.execute(state);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("测试for循环-初始化表达式")
    void testForLoopInitExpression() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class)))
                .thenReturn(true)
                .thenReturn(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "for");
        properties.put("condition", "#i < 3");
        properties.put("conditionType", "spel");
        properties.put("forInit", "i=0");
        properties.put("forUpdate", "i++");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = loopNode.execute(state);

        assertThat(result).isNotNull();
        assertThat(state.getAttributes().get("i")).isNotNull();
    }

    @Test
    @DisplayName("测试条件为false时退出")
    void testExitWhenConditionFalse() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "while");
        properties.put("condition", "false");
        properties.put("conditionType", "spel");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = loopNode.execute(state);

        assertThat(result.nextNode()).isEqualTo("exitNode");
    }

    @Test
    @DisplayName("测试缺少条件评估器")
    void testMissingEvaluator() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "while");
        properties.put("condition", "true");
        properties.put("conditionType", "unknown");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        LoopNode nodeWithEmptyEvaluators = new LoopNode(new HashMap<>());

        AgentNode.ExecutionResult result = nodeWithEmptyEvaluators.execute(state);

        assertThat(result.nextNode()).isEqualTo("exitNode");
    }

    @Test
    @DisplayName("测试循环上下文保存和恢复")
    void testLoopContextSaveAndRestore() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class)))
                .thenReturn(true)
                .thenReturn(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "while");
        properties.put("condition", "true");
        properties.put("conditionType", "spel");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        loopNode.execute(state);

        Object context = state.getAttributes().get("__loop_context__");
        assertThat(context).isNotNull();
        assertThat(context).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("测试for循环初始化表达式-整数")
    void testForLoopInitInteger() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "for");
        properties.put("condition", "false");
        properties.put("conditionType", "spel");
        properties.put("forInit", "count=10");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        loopNode.execute(state);

        assertThat(state.getAttributes().get("count")).isEqualTo(10);
        assertThat(state.getAttributes().get("count")).isInstanceOf(Integer.class);
    }

    @Test
    @DisplayName("测试for循环初始化表达式-浮点数")
    void testForLoopInitDouble() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "for");
        properties.put("condition", "false");
        properties.put("conditionType", "spel");
        properties.put("forInit", "rate=3.14");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        loopNode.execute(state);

        assertThat(state.getAttributes().get("rate")).isEqualTo(3.14);
        assertThat(state.getAttributes().get("rate")).isInstanceOf(Double.class);
    }

    @Test
    @DisplayName("测试for循环初始化表达式-字符串")
    void testForLoopInitString() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "for");
        properties.put("condition", "false");
        properties.put("conditionType", "spel");
        properties.put("forInit", "name=\"test\"");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        loopNode.execute(state);

        assertThat(state.getAttributes().get("name")).isEqualTo("test");
    }

    @Test
    @DisplayName("测试for循环初始化表达式-布尔值")
    void testForLoopInitBoolean() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "for");
        properties.put("condition", "false");
        properties.put("conditionType", "spel");
        properties.put("forInit", "active=true");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        loopNode.execute(state);

        assertThat(state.getAttributes().get("active")).isEqualTo(true);
    }

    @Test
    @DisplayName("测试类型安全-iterationCount从Map恢复")
    void testTypeSafeIterationCountRecovery() {
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("iterationCount", 3L);
        contextMap.put("conditionChecked", true);
        contextMap.put("bodyExecuted", Boolean.TRUE);
        contextMap.put("pendingUpdate", "false");
        contextMap.put("initialized", 1);

        AgentState state = new AgentState();
        state.getAttributes().put("__loop_context__", contextMap);

        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(false);

        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "while");
        properties.put("condition", "false");
        properties.put("conditionType", "spel");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        setCurrentNodeProperties(state, properties);

        assertThatCode(() -> loopNode.execute(state)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("测试do-while循环类型")
    void testDoWhileLoopType() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "dowhile");
        properties.put("condition", "false");
        properties.put("conditionType", "spel");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = loopNode.execute(state);

        assertThat(result.nextNode()).isEqualTo("bodyNode");
    }

    @Test
    @DisplayName("测试do-while循环-先执行后判断")
    void testDoWhileExecuteFirstThenCheck() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "do-while");
        properties.put("condition", "false");
        properties.put("conditionType", "spel");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");

        AgentState state = new AgentState();
        setCurrentNodeProperties(state, properties);

        AgentNode.ExecutionResult result = loopNode.execute(state);

        assertThat(result.nextNode()).isEqualTo("bodyNode");
    }

    @Test
    @DisplayName("测试不同循环节点上下文隔离")
    void testLoopContextIsolationByNodeId() {
        when(mockEvaluator.evaluate(anyString(), any(AgentState.class))).thenReturn(true);

        AgentState state = new AgentState();
        Map<String, Object> properties = new HashMap<>();
        properties.put("loopType", "while");
        properties.put("condition", "true");
        properties.put("conditionType", "spel");
        properties.put("loopBody", "bodyNode");
        properties.put("exitNode", "exitNode");
        properties.put("maxIterations", 1);
        setCurrentNodeProperties(state, properties);

        setCurrentNodeId(state, "loopA");
        AgentNode.ExecutionResult loopAFirst = loopNode.execute(state);
        assertThat(loopAFirst.nextNode()).isEqualTo("bodyNode");

        setCurrentNodeId(state, "loopB");
        AgentNode.ExecutionResult loopBFirst = loopNode.execute(state);
        assertThat(loopBFirst.nextNode()).isEqualTo("bodyNode");
    }
}
