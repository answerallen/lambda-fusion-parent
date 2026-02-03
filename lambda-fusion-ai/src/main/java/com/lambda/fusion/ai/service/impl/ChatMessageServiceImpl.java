package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.entity.ChatMessageEntity;
import com.lambda.fusion.ai.mapper.ChatMessageMapper;
import com.lambda.fusion.ai.model.dto.SendMessageDTO;
import com.lambda.fusion.ai.model.vo.ChatMessageVO;
import com.lambda.fusion.ai.service.ChatMessageService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessageEntity>
        implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;

    @Override
    public ChatMessageVO sendMessage(Long sessionId, SendMessageDTO dto) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setMessageId(IdUtil.fastSimpleUUID());
        entity.setSessionId(sessionId);
        entity.setRole("user");
        entity.setContent(dto.getContent());
        entity.setIsRagEnhanced(false);
        chatMessageMapper.insert(entity);
        // TODO: 调用LLM生成回复
        return entityToVO(entity);
    }

    @Override
    public List<ChatMessageVO> listMessages(Long sessionId, Integer limit) {
        return chatMessageMapper.listBySessionId(sessionId, limit).stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    public void submitFeedback(Long messageId, Integer feedback) {
        ChatMessageEntity entity = chatMessageMapper.selectById(messageId);
        entity.setUserFeedback(feedback);
        chatMessageMapper.updateById(entity);
    }

    private ChatMessageVO entityToVO(ChatMessageEntity entity) {
        ChatMessageVO vo = new ChatMessageVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
