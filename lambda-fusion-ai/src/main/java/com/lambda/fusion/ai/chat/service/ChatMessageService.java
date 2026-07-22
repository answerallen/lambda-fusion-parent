package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import java.util.List;

public interface ChatMessageService {

    void saveUserMessage(ChatSessionEntity session, String content);

    void saveAssistantMessage(ChatSessionEntity session, String content);

    List<ChatMessageEntity> listBySession(String sessionId);

    /**
     * 删除该会话的全部消息。不做租户隔离校验，应由上层调用方先验证会话所有权。
     */
    void deleteBySession(String sessionId);
}
