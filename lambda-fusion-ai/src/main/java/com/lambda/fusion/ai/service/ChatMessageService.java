package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.ChatHistory;
import com.lambda.fusion.ai.model.SendMessage;
import com.lambda.fusion.ai.model.entity.ChatMessageEntity;
import java.util.List;

public interface ChatMessageService extends IService<ChatMessageEntity> {
    ChatHistory sendMessage(Long sessionId, SendMessage dto);

    void sendMessageStream(Long sessionId, SendMessage dto);

    List<ChatHistory> listMessages(Long sessionId, Integer limit);

    void submitFeedback(Long sessionId, String messageId, Integer feedback);
}
