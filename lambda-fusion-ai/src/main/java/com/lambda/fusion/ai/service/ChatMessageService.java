package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.entity.ChatMessageEntity;
import com.lambda.fusion.ai.model.dto.SendMessageDTO;
import com.lambda.fusion.ai.model.vo.ChatMessageVO;
import java.util.List;

public interface ChatMessageService extends IService<ChatMessageEntity> {
    ChatMessageVO sendMessage(Long sessionId, SendMessageDTO dto);

    void sendMessageStream(
            Long sessionId,
            SendMessageDTO dto,
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter);

    List<ChatMessageVO> listMessages(Long sessionId, Integer limit);

    void submitFeedback(Long messageId, Integer feedback);
}
