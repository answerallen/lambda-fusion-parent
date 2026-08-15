package com.lambda.fusion.ai.chat.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.lambda.fusion.ai.chat.runtime.agui.AgentEventInterpreter;
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
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link AgentEventInterpreter}：agentscope AgentEvent -> AG-UI AguiEvent 映射，
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
        AgentEventInterpreter mapper = newMapper(true);
        List<AguiEvent> events = events(mapper, new TextBlockDeltaEvent("reply-1", "block-1", "你好"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.TextMessageStart.class);
        assertThat(events.get(1)).isInstanceOf(AguiEvent.TextMessageContent.class);
        AguiEvent.TextMessageContent content = (AguiEvent.TextMessageContent) events.get(1);
        assertThat(content.delta()).isEqualTo("你好");
        assertThat(content.messageId()).isEqualTo("reply-1");
    }

    @Test
    void sameReplyIdDoesNotEmitDuplicateStart() {
        AgentEventInterpreter mapper = newMapper(true);
        events(mapper, new TextBlockDeltaEvent("reply-1", "block-1", "a"));
        List<AguiEvent> events = events(mapper, new TextBlockDeltaEvent("reply-1", "block-2", "b"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.TextMessageContent.class);
    }

    @Test
    void encodeToJsonUsesCamelCaseAndAguiType() {
        AgentEventInterpreter mapper = newMapper(true);
        events(mapper, new TextBlockDeltaEvent("reply-1", "block-1", "hi"));
        List<AguiEvent> events = events(mapper, new TextBlockDeltaEvent("reply-1", "block-2", " world"));
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
        AgentEventInterpreter mapper = newMapper(true);
        events(mapper, new ToolCallStartEvent("reply-1", "tc-1", "search"));
        events(mapper, new ToolCallDeltaEvent("reply-1", "tc-1", "search", "{\"q\":\"x\"}"));
        events(mapper, new ToolCallEndEvent("reply-1", "tc-1", "search"));
        events(mapper, new ToolResultStartEvent("reply-1", "tc-1", "search"));
        events(mapper, new ToolResultTextDeltaEvent("reply-1", "tc-1", "search", "result-text"));
        List<AguiEvent> events =
                events(mapper, new ToolResultEndEvent("reply-1", "tc-1", "search", ToolResultState.SUCCESS));

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
    void toolResultStartDoesNotRepeatToolCallStart() {
        AgentEventInterpreter mapper = newMapper(true);
        events(mapper, new ToolCallStartEvent("reply-1", "tc-1", "search"));

        assertThat(events(mapper, new ToolResultStartEvent("reply-1", "tc-1", "search")))
                .isEmpty();
    }

    @Test
    void reasoningEmitsOnlyWhenEnabled() {
        AgentEventInterpreter enabled = newMapper(true);
        List<AguiEvent> withReasoning = events(enabled, new ThinkingBlockDeltaEvent("r-1", "b-1", "思考"));
        assertThat(withReasoning).hasSize(3);
        assertThat(withReasoning.get(0)).isInstanceOf(AguiEvent.ReasoningStart.class);
        assertThat(withReasoning.get(1)).isInstanceOf(AguiEvent.ReasoningMessageStart.class);

        AgentEventInterpreter disabled = newMapper(false);
        assertThat(events(disabled, new ThinkingBlockDeltaEvent("r-1", "b-1", "思考")))
                .isEmpty();
    }

    @Test
    void toolCallClosesActiveTextMessage() {
        AgentEventInterpreter mapper = newMapper(true);
        events(mapper, new TextBlockDeltaEvent("reply-1", "b-1", "text"));
        // 工具调用开始应先关闭活跃的文本消息
        List<AguiEvent> events = events(mapper, new ToolCallStartEvent("reply-1", "tc-1", "search"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.TextMessageEnd.class);
        assertThat(events.get(1)).isInstanceOf(AguiEvent.ToolCallStart.class);
    }

    @Test
    void requireUserConfirmEmitsRunFinishedInterrupt() {
        AgentEventInterpreter mapper = newMapper(true);
        ToolUseBlock toolUse =
                ToolUseBlock.builder().id("call-1").name("query_date").build();
        List<AguiEvent> events = events(mapper, new RequireUserConfirmEvent("reply-1", List.of(toolUse)));

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.RunFinished.class);
        AguiEvent.RunFinished finished = (AguiEvent.RunFinished) events.get(0);
        assertThat(finished.outcome()).isInstanceOf(AguiEvent.RunFinishedInterruptOutcome.class);
        AguiEvent.RunFinishedInterruptOutcome outcome = (AguiEvent.RunFinishedInterruptOutcome) finished.outcome();
        assertThat(outcome.interrupts()).hasSize(1);
        AguiEvent.Interrupt interrupt = outcome.interrupts().get(0);
        assertThat(interrupt.toolCallId()).isEqualTo("call-1");
        assertThat(interrupt.reason()).isEqualTo("human_confirmation_required");
        String json = mapper.encodeToJson(finished);
        assertThat(json).contains("\"type\":\"RUN_FINISHED\"");
        assertThat(json).contains("\"interrupts\"");
    }

    @Test
    void rootAgentStartEmitsSingleRunStarted() {
        AgentEventInterpreter mapper = newMapper(true);
        List<AguiEvent> events = events(mapper, new AgentStartEvent("session-1", "reply-1", "root"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.RunStarted.class);
    }

    @Test
    void childAgentStartDoesNotEmitRunStarted() {
        AgentEventInterpreter mapper = newMapper(true);
        AgentEvent childStart = new AgentStartEvent("session-1", "reply-1", "sub").withSource("sub-agent");
        List<AguiEvent> events = events(mapper, childStart);

        assertThat(events).isEmpty();
    }

    @Test
    void encodeRunErrorEmitsJson() {
        AgentEventInterpreter mapper = newMapper(true);
        AguiEvent.RunError runError = new AguiEvent.RunError(THREAD_ID, RUN_ID, "boom", null);

        String json = mapper.encodeToJson(runError);
        assertThat(json).contains("\"type\":\"RUN_ERROR\"");
        assertThat(json).contains("\"message\":\"boom\"");
    }

    private static AgentEventInterpreter newMapper(boolean enableReasoning) {
        return new AgentEventInterpreter(THREAD_ID, RUN_ID, enableReasoning);
    }

    private static List<AguiEvent> events(
            AgentEventInterpreter interpreter, io.agentscope.core.event.AgentEvent event) {
        return interpreter.interpret(event).events();
    }
}
