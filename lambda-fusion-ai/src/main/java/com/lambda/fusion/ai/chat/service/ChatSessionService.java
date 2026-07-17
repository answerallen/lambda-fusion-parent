package com.lambda.fusion.ai.chat.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lambda.fusion.ai.chat.model.ChatSession;
import com.lambda.fusion.ai.chat.model.CreateSession;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import java.util.List;

public interface ChatSessionService extends IService<ChatSessionEntity> {
    ChatSession createSession(CreateSession dto);

    List<ChatSession> listUserSessions(String userId);

    void archiveSession(String sessionId);
}
