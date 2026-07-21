package com.lambda.fusion.ai.chat.service;

import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import java.util.List;

/**
 * 对话消息服务。
 *
 * @author Jin
 */
public interface ChatMessageService {

    void saveUserMessage(ChatSessionEntity session, String content);

    void saveAssistantMessage(ChatSessionEntity session, String content);

    List<ChatMessageEntity> listBySession(String sessionId);

    void deleteBySession(String sessionId);
}
