package com.lambda.fusion.ai.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.chat.mapper.ChatMessageMapper;
import com.lambda.fusion.ai.chat.model.ChatMessageView;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.core.utils.AuthUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatAttachmentService chatAttachmentService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageEntity saveUserMessage(ChatSessionEntity session, String content) {
        return save(session, "user", content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageEntity saveAssistantMessage(ChatSessionEntity session, String content) {
        return save(session, "assistant", content);
    }

    @Override
    public List<ChatMessageView> listBySession(String sessionId) {
        List<ChatMessageEntity> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .eq(ChatMessageEntity::getTenantId, AuthUtils.getTenantId())
                .orderByAsc(ChatMessageEntity::getId));
        List<Long> messageIds = messages.stream().map(ChatMessageEntity::getId).toList();
        Map<Long, List<ChatAttachmentEntity>> attachmentsByMessage =
                chatAttachmentService.listByMessageIds(messageIds).stream()
                        .collect(Collectors.groupingBy(ChatAttachmentEntity::getMessageId));
        return messages.stream()
                .map(m -> ChatMessageView.of(m, attachmentsByMessage.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySession(String sessionId) {
        chatMessageMapper.delete(
                new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, sessionId));
    }

    private ChatMessageEntity save(ChatSessionEntity session, String role, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setTenantId(session.getTenantId());
        entity.setSessionId(session.getId());
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(entity);
        return entity;
    }
}
