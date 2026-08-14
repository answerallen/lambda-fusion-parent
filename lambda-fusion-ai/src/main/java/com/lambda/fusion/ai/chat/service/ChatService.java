package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.ChatRun;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.SendMessage;
import java.util.Optional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 对话入口：创建后台 Run、订阅事件、恢复、确认和停止。 */
public interface ChatService {

    SseEmitter streamChat(String sessionId, SendMessage message);

    Optional<ChatRun> activeRun(String sessionId);

    ChatRun getRun(String sessionId, String runId);

    SseEmitter resume(String sessionId, String runId, long afterSeq, boolean bootstrap);

    SseEmitter confirm(String sessionId, String runId, ConfirmToolCall command);

    void stop(String sessionId, String runId);
}
