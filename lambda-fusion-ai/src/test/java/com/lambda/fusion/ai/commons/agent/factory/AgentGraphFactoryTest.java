package com.lambda.fusion.ai.commons.agent.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lambda.fusion.ai.agent.AgentGraph;
import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.evaluator.ConditionEvaluator;
import com.lambda.fusion.ai.agent.factory.AgentGraphBuildOptions;
import com.lambda.fusion.ai.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.agent.model.EdgeDefinition;
import com.lambda.fusion.ai.agent.model.GraphDefinition;
import com.lambda.fusion.ai.agent.model.NodeDefinition;
import com.lambda.fusion.ai.exception.AiBusinessException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.CompileConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AgentGraphFactoryTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private AgentNode agentNode;

    @Test
    @DisplayName("构建图时应用编译配置和最大迭代次数")
    void testBuildFromDefinitionWithOptions() throws Exception {
        AgentGraphFactory factory = new AgentGraphFactory(applicationContext, new ObjectMapper());
        GraphDefinition definition = new GraphDefinition();
        NodeDefinition node = new NodeDefinition();
        node.setId("start");
        node.setType("TEST_NODE");
        definition.setNodes(List.of(node));
        definition.setEntryPoint("start");

        AgentGraphBuildOptions options = new AgentGraphBuildOptions();
        CompileConfig compileConfig = CompileConfig.builder().build();
        options.setCompileConfig(compileConfig);
        options.setMaxIterations(42);

        when(applicationContext.getBeansOfType(AgentNode.class)).thenReturn(Map.of("testNode", agentNode));
        when(applicationContext.getBeansOfType(ConditionEvaluator.class)).thenReturn(Map.of());
        when(agentNode.getName()).thenReturn("TEST_NODE");

        AgentGraph graph = factory.buildFromDefinition(definition, options);

        assertThat(graph).isNotNull();
        assertThat(readField(graph, "compileConfig")).isSameAs(compileConfig);
        assertThat(readField(graph, "maxIterations")).isEqualTo(42);
    }

    @Test
    @DisplayName("未知 conditionType 时构图失败")
    void testBuildFromDefinitionWithUnknownConditionType() {
        AgentGraphFactory factory = new AgentGraphFactory(applicationContext, new ObjectMapper());
        GraphDefinition definition = new GraphDefinition();
        NodeDefinition start = new NodeDefinition();
        start.setId("start");
        start.setType("TEST_NODE");
        NodeDefinition end = new NodeDefinition();
        end.setId("end");
        end.setType("TEST_NODE");
        EdgeDefinition edge = new EdgeDefinition();
        edge.setSource("start");
        edge.setTarget("end");
        edge.setConditionType("missing");
        edge.setConditionExpression("x > 0");
        definition.setNodes(List.of(start, end));
        definition.setEdges(List.of(edge));
        definition.setEntryPoint("start");

        when(applicationContext.getBeansOfType(AgentNode.class)).thenReturn(Map.of("testNode", agentNode));
        when(applicationContext.getBeansOfType(ConditionEvaluator.class)).thenReturn(Map.of());
        when(agentNode.getName()).thenReturn("TEST_NODE");

        assertThatThrownBy(() -> factory.buildFromDefinition(definition)).isInstanceOf(AiBusinessException.class);
    }

    @Test
    @DisplayName("仅配置 conditionExpression 时构图失败")
    void testBuildFromDefinitionWithConditionExpressionOnly() {
        AgentGraphFactory factory = new AgentGraphFactory(applicationContext, new ObjectMapper());
        GraphDefinition definition = new GraphDefinition();
        NodeDefinition start = new NodeDefinition();
        start.setId("start");
        start.setType("TEST_NODE");
        NodeDefinition end = new NodeDefinition();
        end.setId("end");
        end.setType("TEST_NODE");
        EdgeDefinition edge = new EdgeDefinition();
        edge.setSource("start");
        edge.setTarget("end");
        edge.setConditionExpression("x > 0");
        definition.setNodes(List.of(start, end));
        definition.setEdges(List.of(edge));
        definition.setEntryPoint("start");

        when(applicationContext.getBeansOfType(AgentNode.class)).thenReturn(Map.of("testNode", agentNode));
        when(applicationContext.getBeansOfType(ConditionEvaluator.class)).thenReturn(Map.of());
        when(agentNode.getName()).thenReturn("TEST_NODE");

        assertThatThrownBy(() -> factory.buildFromDefinition(definition)).isInstanceOf(AiBusinessException.class);
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = AgentGraph.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
