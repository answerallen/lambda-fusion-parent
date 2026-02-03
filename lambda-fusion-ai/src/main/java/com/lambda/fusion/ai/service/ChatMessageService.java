package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.model.SendMessage;
import com.lambda.fusion.ai.model.ChatMessage;
import java.util.List;

public interface ChatMessageService extends IService<ChatMessageEntity> {
    ChatMessage sendMessage(Long sessionId, SendMessage dto);

    void sendMessageStream(
            Long sessionId,
            SendMessage dto,
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter);

    List<ChatMessage> listMessages(Long sessionId, Integer limit);

    void submitFeedback(Long messageId, Integer feedback);
}
