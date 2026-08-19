package com.lambda.fusion.ai.chat.runtime.model;

import com.lambda.fusion.ai.chat.runtime.snapshot.ExecutionSnapshot;
import java.util.List;

/**
 * 执行快照增量；描述一次 AgentEvent 解释后对执行快照应产生的语义变化。
 *
 * @author Jin
 */
public record ExecutionSnapshotDelta(
        String textMessageId,
        String textDelta,
        String reasoningMessageId,
        String reasoningDelta,
        boolean closeText,
        boolean closeReasoning,
        boolean closeActiveMessages,
        List<ToolDelta> tools,
        List<ExecutionSnapshot.Tool> awaitingTools) {

    /** 是否存在快照变化。 */
    public boolean isEmpty() {
        return textDelta == null
                && reasoningDelta == null
                && !closeText
                && !closeReasoning
                && !closeActiveMessages
                && (tools == null || tools.isEmpty())
                && (awaitingTools == null || awaitingTools.isEmpty());
    }

    /** 工具调用增量。 */
    public record ToolDelta(
            String toolCallId,
            String toolCallName,
            String argsDelta,
            String resultDelta,
            String status,
            boolean replaceArgs,
            boolean replaceResult) {}
}
