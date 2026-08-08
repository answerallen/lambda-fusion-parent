package com.lambda.fusion.ai.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolResultState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link AguiEventMapper}：agentscope AgentEvent -> AG-UI AguiEvent 映射，
 * 以及 SSE JSON 序列化使用 camelCase 字段 + AG-UI type 标识符（前端 AGUIAdapter
 * 依赖 JSON.parse 后按 camelCase 读 event.type / event.delta / event.toolCallId）。
 *
 * @author Jin
 */
class AguiEventMapperTest {

    private static final String THREAD_ID = "session-1";
    private static final String RUN_ID = "run-1";

    @Test
    void textBlockDeltaEmitsStartThenContent() {
        AguiEventMapper mapper = newMapper(true);
        List<AguiEvent> events = mapper.map(new TextBlockDeltaEvent("reply-1", "block-1", "你好"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.TextMessageStart.class);
        assertThat(events.get(1)).isInstanceOf(AguiEvent.TextMessageContent.class);
        AguiEvent.TextMessageContent content = (AguiEvent.TextMessageContent) events.get(1);
        assertThat(content.delta()).isEqualTo("你好");
        assertThat(content.messageId()).isEqualTo("reply-1");
    }

    @Test
    void sameReplyIdDoesNotEmitDuplicateStart() {
        AguiEventMapper mapper = newMapper(true);
        mapper.map(new TextBlockDeltaEvent("reply-1", "block-1", "a"));
        List<AguiEvent> events = mapper.map(new TextBlockDeltaEvent("reply-1", "block-2", "b"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.TextMessageContent.class);
    }

    @Test
    void encodeToJsonUsesCamelCaseAndAguiType() {
        AguiEventMapper mapper = newMapper(true);
        mapper.map(new TextBlockDeltaEvent("reply-1", "block-1", "hi"));
        List<AguiEvent> events = mapper.map(new TextBlockDeltaEvent("reply-1", "block-2", " world"));
        AguiEvent content = events.stream()
                .filter(e -> e instanceof AguiEvent.TextMessageContent)
                .findFirst()
                .orElseThrow();
        String json = mapper.encodeToJson(content);

        // 前导空格 + camelCase 字段 + AG-UI type 标识符
        assertThat(json).startsWith(" {");
        assertThat(json).contains("\"type\":\"TEXT_MESSAGE_CONTENT\"");
        assertThat(json).contains("\"messageId\":\"reply-1\"");
        assertThat(json).contains("\"delta\":\" world\"");
        assertThat(json).contains("\"threadId\":\"session-1\"");
        assertThat(json).contains("\"runId\":\"run-1\"");
        // 禁止 snake_case（前端 AGUIAdapter 按 camelCase 读取）
        assertThat(json).doesNotContain("message_id", "tool_call_id", "thread_id");
    }

    @Test
    void toolCallSequenceEmitsStartArgsEndResult() {
        AguiEventMapper mapper = newMapper(true);
        mapper.map(new ToolCallStartEvent("reply-1", "tc-1", "search"));
        mapper.map(new ToolCallDeltaEvent("reply-1", "tc-1", "search", "{\"q\":\"x\"}"));
        mapper.map(new ToolCallEndEvent("reply-1", "tc-1", "search"));
        mapper.map(new ToolResultStartEvent("reply-1", "tc-1", "search"));
        mapper.map(new ToolResultTextDeltaEvent("reply-1", "tc-1", "search", "result-text"));
        List<AguiEvent> events =
                mapper.map(new ToolResultEndEvent("reply-1", "tc-1", "search", ToolResultState.SUCCESS));

        // ToolResultEnd -> ToolCallEnd + ToolCallResult
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.ToolCallEnd.class);
        assertThat(events.get(1)).isInstanceOf(AguiEvent.ToolCallResult.class);
        AguiEvent.ToolCallResult result = (AguiEvent.ToolCallResult) events.get(1);
        assertThat(result.content()).isEqualTo("result-text");
        assertThat(result.role()).isEqualTo("tool");
        String json = mapper.encodeToJson(result);
        assertThat(json).contains("\"type\":\"TOOL_CALL_RESULT\"");
        assertThat(json).contains("\"toolCallId\":\"tc-1\"");
        assertThat(json).contains("\"content\":\"result-text\"");
    }

    @Test
    void reasoningEmitsOnlyWhenEnabled() {
        AguiEventMapper enabled = newMapper(true);
        List<AguiEvent> withReasoning = enabled.map(new ThinkingBlockDeltaEvent("r-1", "b-1", "思考"));
        assertThat(withReasoning).hasSize(2);
        assertThat(withReasoning.get(0)).isInstanceOf(AguiEvent.ReasoningMessageStart.class);

        AguiEventMapper disabled = newMapper(false);
        assertThat(disabled.map(new ThinkingBlockDeltaEvent("r-1", "b-1", "思考")))
                .isEmpty();
    }

    @Test
    void toolCallClosesActiveTextMessage() {
        AguiEventMapper mapper = newMapper(true);
        mapper.map(new TextBlockDeltaEvent("reply-1", "b-1", "text"));
        // 工具调用开始应先关闭活跃的文本消息
        List<AguiEvent> events = mapper.map(new ToolCallStartEvent("reply-1", "tc-1", "search"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.TextMessageEnd.class);
        assertThat(events.get(1)).isInstanceOf(AguiEvent.ToolCallStart.class);
    }

    private static AguiEventMapper newMapper(boolean enableReasoning) {
        return new AguiEventMapper(THREAD_ID, RUN_ID, enableReasoning);
    }
}
