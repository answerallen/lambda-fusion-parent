package com.lambda.fusion.ai.chat.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lambda.fusion.ai.chat.model.ChatHistory;
import com.lambda.fusion.ai.chat.model.SendMessage;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import java.util.List;

public interface ChatMessageService extends IService<ChatMessageEntity> {
    void sendMessageStream(String sessionId, SendMessage dto);

    List<ChatHistory> listMessages(String sessionId, Integer limit);

    void submitFeedback(String sessionId, String messageId, Integer feedback);
}
