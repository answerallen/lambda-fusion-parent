package com.lambda.fusion.ai.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.apps.service.AppService;
import com.lambda.fusion.ai.chat.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.chat.model.ChatSessionPage;
import com.lambda.fusion.ai.chat.model.CreateSession;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.chat.service.ChatMessageService;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.core.utils.AuthUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final AppService appService;
    private final ChatMessageService chatMessageService;
    private final ChatAttachmentService chatAttachmentService;

    @Override
    public Page<ChatSessionEntity> page(ChatSessionPage query) {
        return chatSessionMapper.selectPage(query.getPage(), query.getLambdaQueryWrapper());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionEntity create(CreateSession dto) {
        var app = appService.loadAvailable(dto.getAppId());
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setAppId(dto.getAppId());
        entity.setTenantId(AuthUtils.getTenantId());
        entity.setUserId(AuthUtils.getUser().getUsername());
        entity.setTitle(StringUtils.defaultIfBlank(dto.getTitle(), app.getName()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setLastMessageAt(LocalDateTime.now());
        chatSessionMapper.insert(entity);
        return entity;
    }

    @Override
    public ChatSessionEntity get(String id) {
        return loadOwned(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        loadOwned(id);
        chatAttachmentService.deleteBySession(id);
        chatMessageService.deleteBySession(id);
        chatSessionMapper.deleteById(id);
    }

    @Override
    public ChatSessionEntity loadOwned(String id) {
        ChatSessionEntity entity = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, id)
                .eq(ChatSessionEntity::getTenantId, AuthUtils.getTenantId())
                .eq(ChatSessionEntity::getUserId, AuthUtils.getUser().getUsername()));
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.CHAT_SESSION_NOT_FOUND, id);
        }
        return entity;
    }

    @Override
    public void touchLastMessageAt(String id) {
        // 仅更新 time 字段，不做存在性/所有权校验；ID 无效时 updateById 无影响
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(id);
        entity.setLastMessageAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(entity);
    }

    @Override
    public void updatePendingConfirm(String id, String pendingConfirmJson) {
        // LambdaUpdateWrapper 显式 set 含 null，绕过 MyBatis-Plus 默认不更新 null 字段，使清空生效。
        chatSessionMapper.update(
                null,
                new LambdaUpdateWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getId, id)
                        .set(ChatSessionEntity::getPendingConfirm, pendingConfirmJson)
                        .set(ChatSessionEntity::getUpdatedAt, LocalDateTime.now()));
    }
}
