package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.ChatHistory;
import com.lambda.fusion.ai.model.SendMessage;
import com.lambda.fusion.ai.model.entity.ChatMessageEntity;
import java.util.List;

public interface ChatMessageService extends IService<ChatMessageEntity> {
    void sendMessageStream(String sessionId, SendMessage dto);

    List<ChatHistory> listMessages(String sessionId, Integer limit);

    void submitFeedback(String sessionId, String messageId, Integer feedback);
}
