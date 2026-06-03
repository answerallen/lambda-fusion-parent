package com.lambda.fusion.ai.commons.agent.factory;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.agent.factory.AgentGraphProvider;
import com.lambda.fusion.ai.agent.node.LlmProcessingNode;
import com.lambda.fusion.ai.agent.node.ToolExecutingNode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RagAgentGraphProviderTest {

    @Test
    void shouldReuseSameGraphInstance() {
        LlmProcessingNode llmProcessingNode = Mockito.mock(LlmProcessingNode.class);
        ToolExecutingNode toolExecutingNode = Mockito.mock(ToolExecutingNode.class);
        Mockito.when(llmProcessingNode.getName()).thenReturn(LlmProcessingNode.NAME);
        Mockito.when(toolExecutingNode.getName()).thenReturn(ToolExecutingNode.NAME);

        AgentGraphProvider provider = new AgentGraphProvider(llmProcessingNode, toolExecutingNode);

        var first = provider.getGraph();
        var second = provider.getGraph();

        assertThat(first).isSameAs(second);
    }
}
