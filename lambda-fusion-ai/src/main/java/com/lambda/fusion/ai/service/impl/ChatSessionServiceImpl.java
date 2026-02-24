package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.AiConstants.Enums.SessionStatus;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
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
        entity.setStatus(SessionStatus.ACTIVE.name());
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

        // 使用乐观锁更新，防止并发修改
        entity.setStatus(SessionStatus.ARCHIVED.name());
        int updatedRows = chatSessionMapper.updateByIdWithVersion(entity);

        if (updatedRows == 0) {
            // 版本冲突，说明有其他线程同时修改了该会话
            log.warn("会话{}存在并发修改，更新失败", sessionId);
            throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "会话已被其他操作修改，请重试");
        }

        log.info("会话{}已归档", sessionId);
    }

    private ChatSession entityToVO(ChatSessionEntity entity) {
        ChatSession vo = new ChatSession();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
