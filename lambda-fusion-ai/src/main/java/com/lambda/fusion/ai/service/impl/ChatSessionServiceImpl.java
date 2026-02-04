package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.model.ChatSession;
import com.lambda.fusion.ai.model.CreateSession;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.service.ChatSessionService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSessionEntity>
        implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;

    @Override
    public ChatSession createSession(CreateSession dto) {
        ChatSessionEntity entity = new ChatSessionEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setSessionId(IdUtil.fastSimpleUUID());
        entity.setMessageCount(0);
        entity.setTotalTokens(0);
        entity.setStatus("ACTIVE");
        chatSessionMapper.insert(entity);
        return entityToVO(entity);
    }

    @Override
    public List<ChatSession> listUserSessions(Long userId) {
        return chatSessionMapper.listByUserId(userId).stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    public void archiveSession(Long sessionId) {
        // 验证输入参数
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.SESSION_NOT_FOUND, "会话ID不能为空");
        }

        ChatSessionEntity entity = chatSessionMapper.selectById(sessionId);
        if (entity == null) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }

        entity.setStatus("ARCHIVED");
        chatSessionMapper.updateById(entity);
    }

    private ChatSession entityToVO(ChatSessionEntity entity) {
        ChatSession vo = new ChatSession();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
