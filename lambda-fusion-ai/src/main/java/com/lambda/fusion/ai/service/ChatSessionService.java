package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.model.CreateSession;
import com.lambda.fusion.ai.model.ChatSession;
import java.util.List;

public interface ChatSessionService extends IService<ChatSessionEntity> {
    ChatSession createSession(CreateSession dto);

    List<ChatSession> listUserSessions(Long userId);

    void archiveSession(Long sessionId);
}
