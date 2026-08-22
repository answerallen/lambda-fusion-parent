package com.lambda.fusion.ai.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.chat.runtime.agui.AgentEventAguiMapper;
import com.lambda.fusion.ai.chat.runtime.agui.AguiEventJsonCodec;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Verifies the direct AgentScope event to official AG-UI event projection. */
class AguiEventMapperTest {

    private static final String THREAD_ID = "session-1";
    private static final String AGUI_RUN_ID = "phase-1";
    private static final String CHAT_RUN_ID = "run-1";

    @Test
    void textBlockDeltaEmitsStartThenContent() {
        AgentEventAguiMapper mapper = newMapper(true);

        List<AguiEvent> events = mapper.map(new TextBlockDeltaEvent("reply-1", "block-1", "hello"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.TextMessageStart.class);
        AguiEvent.TextMessageContent content = (AguiEvent.TextMessageContent) events.get(1);
        assertThat(content.delta()).isEqualTo("hello");
        assertThat(content.messageId()).isEqualTo("reply-1");
    }

    @Test
    void sameReplyIdDoesNotEmitDuplicateStart() {
        AgentEventAguiMapper mapper = newMapper(true);
        mapper.map(new TextBlockDeltaEvent("reply-1", "block-1", "a"));

        assertThat(mapper.map(new TextBlockDeltaEvent("reply-1", "block-2", "b")))
                .singleElement()
                .isInstanceOf(AguiEvent.TextMessageContent.class);
    }

    @Test
    void officialEncodingUsesCamelCaseAndDoesNotExposeInternalCursor() {
        AgentEventAguiMapper mapper = newMapper(true);
        List<AguiEvent> events = mapper.map(new TextBlockDeltaEvent("reply-1", "block-1", "hello"));

        String json = AguiEventJsonCodec.encodeRunEvent(events.getLast(), CHAT_RUN_ID, AGUI_RUN_ID);

        assertThat(json)
                .contains("\"type\":\"TEXT_MESSAGE_CONTENT\"")
                .contains("\"messageId\":\"reply-1\"")
                .contains("\"threadId\":\"session-1\"")
                .contains("\"runId\":\"phase-1\"")
                .contains("\"chatRunId\":\"run-1\"")
                .doesNotContain("message_id", "thread_id", "bootstrapSeq", "\"seq\"");
    }

    @Test
    void toolCallSequenceEmitsEndAndBufferedResultAtResultEnd() {
        AgentEventAguiMapper mapper = newMapper(true);
        mapper.map(new ToolCallStartEvent("reply-1", "tc-1", "search"));
        mapper.map(new ToolCallDeltaEvent("reply-1", "tc-1", "search", "{\"q\":\"x\"}"));
        assertThat(mapper.map(new ToolCallEndEvent("reply-1", "tc-1", "search")))
                .isEmpty();
        assertThat(mapper.map(new ToolResultStartEvent("reply-1", "tc-1", "search")))
                .isEmpty();
        mapper.map(new ToolResultTextDeltaEvent("reply-1", "tc-1", "search", "result-text"));

        List<AguiEvent> events =
                mapper.map(new ToolResultEndEvent("reply-1", "tc-1", "search", ToolResultState.SUCCESS));

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.ToolCallEnd.class);
        AguiEvent.ToolCallResult result = (AguiEvent.ToolCallResult) events.get(1);
        assertThat(result.content()).isEqualTo("result-text");
        assertThat(result.role()).isEqualTo("tool");
    }

    @Test
    void toolCallEndIsEmittedOnlyOnce() {
        AgentEventAguiMapper mapper = newMapper(true);
        mapper.map(new ToolCallStartEvent("reply-1", "tc-1", "search"));
        List<AguiEvent> callEnd = mapper.map(new ToolCallEndEvent("reply-1", "tc-1", "search"));
        mapper.map(new ToolResultStartEvent("reply-1", "tc-1", "search"));
        List<AguiEvent> resultEnd =
                mapper.map(new ToolResultEndEvent("reply-1", "tc-1", "search", ToolResultState.SUCCESS));

        long count = Stream.concat(callEnd.stream(), resultEnd.stream())
                .filter(AguiEvent.ToolCallEnd.class::isInstance)
                .count();
        assertThat(count).isOne();
    }

    @Test
    void reasoningIsOptionalAndToolCallClosesOpenText() {
        AgentEventAguiMapper enabled = newMapper(true);
        assertThat(enabled.map(new ThinkingBlockDeltaEvent("r-1", "b-1", "thought")))
                .hasSize(3);
        assertThat(newMapper(false).map(new ThinkingBlockDeltaEvent("r-1", "b-1", "thought")))
                .isEmpty();

        AgentEventAguiMapper textMapper = newMapper(true);
        textMapper.map(new TextBlockDeltaEvent("reply-1", "b-1", "text"));
        assertThat(textMapper.map(new ToolCallStartEvent("reply-1", "tc-1", "search")))
                .extracting(Object::getClass)
                .containsExactly(AguiEvent.TextMessageEnd.class, AguiEvent.ToolCallStart.class);
    }

    @Test
    void requireUserConfirmEmitsStandardInterruptOutcome() {
        AgentEventAguiMapper mapper = newMapper(true);
        ToolUseBlock toolUse =
                ToolUseBlock.builder().id("call-1").name("query_date").build();

        List<AguiEvent> events = mapper.map(new RequireUserConfirmEvent("reply-1", List.of(toolUse)));

        AguiEvent.RunFinished finished = (AguiEvent.RunFinished) events.getLast();
        AguiEvent.RunFinishedInterruptOutcome outcome = (AguiEvent.RunFinishedInterruptOutcome) finished.outcome();
        assertThat(outcome.interrupts()).singleElement().satisfies(interrupt -> {
            assertThat(interrupt.toolCallId()).isEqualTo("call-1");
            assertThat(interrupt.reason()).isEqualTo("human_confirmation_required");
        });
    }

    @Test
    void onlyRootAgentStartEmitsRunStarted() {
        AgentEventAguiMapper mapper = newMapper(true);
        assertThat(mapper.map(new AgentStartEvent("session-1", "reply-1", "root")))
                .singleElement()
                .isInstanceOf(AguiEvent.RunStarted.class);

        AgentEvent child = new AgentStartEvent("session-1", "reply-1", "sub").withSource("sub-agent");
        assertThat(mapper.map(child)).isEmpty();
    }

    private static AgentEventAguiMapper newMapper(boolean enableReasoning) {
        return new AgentEventAguiMapper(THREAD_ID, AGUI_RUN_ID, enableReasoning);
    }
}
