package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.ChatRun;
import com.lambda.fusion.ai.chat.model.ConfirmToolCall;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.SubmitToolInput;
import java.util.Optional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 对话入口：创建后台 Run、订阅事件、恢复、确认、输入提交和停止。 */
public interface ChatService {

    SseEmitter streamChat(String sessionId, SendMessage message);

    Optional<ChatRun> activeRun(String sessionId);

    ChatRun getRun(String sessionId, String runId);

    SseEmitter resume(String sessionId, String runId);

    SseEmitter confirm(String sessionId, String runId, ConfirmToolCall command);

    SseEmitter submitInput(String sessionId, String runId, SubmitToolInput command);

    void stop(String sessionId, String runId);
}
