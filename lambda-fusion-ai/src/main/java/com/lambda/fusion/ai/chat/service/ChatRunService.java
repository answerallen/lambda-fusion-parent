package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.ChatRun;
import com.lambda.fusion.ai.chat.model.ChatRunStatus;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatRunEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.run.RunSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 对话 Run 的事务状态机。 */
public interface ChatRunService {

    RunContext createOrLoad(String sessionId, SendMessage message);

    Optional<ChatRun> getActiveOwned(String sessionId);

    ChatRun getOwned(String sessionId, String runId);

    RunContext loadOwned(String sessionId, String runId);

    ConfirmTransition confirm(String sessionId, String runId, ConfirmToolCall command);

    boolean claimCreated(ChatRunEntity run);

    boolean checkpoint(ChatRunEntity run, RunSnapshot snapshot, long seq);

    boolean awaitConfirm(ChatRunEntity run, RunSnapshot snapshot, long seq, LocalDateTime deadline);

    boolean requestStopping(ChatRunEntity run);

    boolean requestConfirmationTimeout(ChatRunEntity run, LocalDateTime deadline);

    FinalizeResult finalizeRun(
            ChatRunEntity run,
            ChatRunStatus targetStatus,
            String finishReason,
            RunSnapshot snapshot,
            String toolCallJson,
            long lastSeq,
            String errorCode,
            String errorMessage);

    void recordTerminalSeq(ChatRunEntity run, RunSnapshot snapshot, long seq);

    ChatRunEntity loadCurrent(String runId);

    ChatSessionEntity loadSession(ChatRunEntity run);

    List<ChatRunEntity> listCreated();

    List<ChatRunEntity> listInterruptedOnRestart();

    List<ChatRunEntity> listExpiredConfirmations(LocalDateTime deadline);

    record RunContext(ChatRunEntity run, ChatSessionEntity session) {}

    record ConfirmTransition(ChatRunEntity run, ChatSessionEntity session, boolean resumed) {}

    record FinalizeResult(
            boolean committed,
            Long assistantMessageId,
            String status,
            String finishReason,
            String errorCode,
            String errorMessage) {}
}
