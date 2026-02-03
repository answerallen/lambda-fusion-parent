package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.entity.ChatSessionEntity;
import com.lambda.fusion.ai.model.dto.CreateSessionDTO;
import com.lambda.fusion.ai.model.vo.ChatSessionVO;

import java.util.List;

public interface ChatSessionService extends IService<ChatSessionEntity> {
    ChatSessionVO createSession(CreateSessionDTO dto);

    List<ChatSessionVO> listUserSessions(Long userId);

    void archiveSession(Long sessionId);
}
