package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.entity.ChatSessionEntity;
import com.lambda.fusion.ai.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.model.dto.CreateSessionDTO;
import com.lambda.fusion.ai.model.vo.ChatSessionVO;
import com.lambda.fusion.ai.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSessionEntity>
        implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;

    @Override
    public ChatSessionVO createSession(CreateSessionDTO dto) {
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
    public List<ChatSessionVO> listUserSessions(Long userId) {
        return chatSessionMapper.listByUserId(userId).stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    public void archiveSession(Long sessionId) {
        ChatSessionEntity entity = chatSessionMapper.selectById(sessionId);
        entity.setStatus("ARCHIVED");
        chatSessionMapper.updateById(entity);
    }

    private ChatSessionVO entityToVO(ChatSessionEntity entity) {
        ChatSessionVO vo = new ChatSessionVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
