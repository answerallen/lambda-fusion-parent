package com.lambda.fusion.ai.chat.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.chat.attachment.ChatAttachmentPreviewTokenService;
import com.lambda.fusion.ai.chat.mapper.ChatAttachmentMapper;
import com.lambda.fusion.ai.chat.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.chat.model.ChatAttachmentView;
import com.lambda.fusion.ai.chat.model.entity.ChatAttachmentEntity;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatAttachmentService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.rag.storage.DocumentFileStorage;
import com.lambda.fusion.ai.rag.storage.DocumentFileStorageResolver;
import com.lambda.fusion.core.utils.AuthUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 对话附件服务实现。
 *
 * <p>上传先落临时文件再经 {@link DocumentFileStorage} 持久化（与知识库文档同一套存储后端，
 * 复用 {@code rag.document-storage} 配置）；附件相对路径用独立前缀
 * {@code chat/{tenantId}/{sessionId}/{attachmentId}.{ext}} 与知识库文件隔离。
 *
 * <p>孤儿附件（上传后未发送，message_id IS NULL）由 {@link #delete} 主动撤销或
 * {@link #deleteBySession} 会话级联清理，未引入定时清理任务。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

    private static final String CATEGORY_IMAGE = "IMAGE";
    private static final String CATEGORY_DOCUMENT = "DOCUMENT";

    private final ChatAttachmentMapper chatAttachmentMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final DocumentFileStorageResolver storageResolver;
    private final AiProperties aiProperties;
    private final ChatAttachmentPreviewTokenService previewTokenService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatAttachmentEntity upload(String sessionId, MultipartFile file) {
        ChatSessionEntity session = chatSessionMapper.selectChatSessionByIdAndUserId(sessionId,AuthUtils.getUsername());
        AiProperties.Chat.Attachment cfg = aiProperties.getChat().getAttachment();
        String extension = resolveExtension(file, cfg);
        validateSize(file, cfg);

        ChatAttachmentEntity entity = new ChatAttachmentEntity();
        entity.setId(IdUtil.getSnowflakeNextIdStr());
        entity.setSessionId(session.getId());
        entity.setFileName(StringUtils.defaultIfBlank(file.getOriginalFilename(), entity.getId()));
        entity.setFileType(extension);
        entity.setMimeType(file.getContentType());
        entity.setSizeBytes(file.getSize());
        entity.setCategory(resolveCategory(extension, cfg));
        entity.setCreatedAt(LocalDateTime.now());
        chatAttachmentMapper.insert(entity);

        // MultipartFile 内容在请求结束后即被清理，先落临时文件再持久化；finally 立即删除
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("chat-att-", "." + extension);
            file.transferTo(tempFile);
            DocumentFileStorage storage = storageResolver.resolve(
                    aiProperties.getRag().getDocumentStorage().getType());
            String relativeName = "chat/" + session.getId() + "/" + entity.getId() + "." + extension;
            entity.setStorageType(storage.type());
            entity.setStoragePath(storage.store(tempFile, relativeName));
            chatAttachmentMapper.updateById(entity);
        } catch (IOException e) {
            chatAttachmentMapper.deleteById(entity.getId());
            throw new AiBusinessException(AiErrorCode.ATTACHMENT_STORAGE_ERROR, e);
        } catch (RuntimeException e) {
            chatAttachmentMapper.deleteById(entity.getId());
            throw e instanceof AiBusinessException aiBusinessException
                    ? aiBusinessException
                    : new AiBusinessException(AiErrorCode.ATTACHMENT_STORAGE_ERROR, e);
        } finally {
            deleteQuietly(tempFile);
        }
        return entity;
    }

    @Override
    public ChatAttachmentEntity loadOwned(String attachmentId) {
        ChatAttachmentEntity entity = chatAttachmentMapper.selectOne(new LambdaQueryWrapper<ChatAttachmentEntity>()
                .eq(ChatAttachmentEntity::getId, attachmentId));
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.ATTACHMENT_NOT_FOUND, attachmentId);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ChatAttachmentEntity> bindToMessage(
            ChatSessionEntity session, List<String> attachmentIds, Long messageId) {
        List<ChatAttachmentEntity> attachments = new java.util.ArrayList<>();
        for (String attachmentId : attachmentIds) {
            ChatAttachmentEntity entity = loadOwned(attachmentId);
            boolean sessionMatched = session.getId().equals(entity.getSessionId());
            boolean bindable =
                    entity.getMessageId() == null || entity.getMessageId().equals(messageId);
            if (!sessionMatched || !bindable) {
                throw new AiBusinessException(AiErrorCode.ATTACHMENT_NOT_FOUND, attachmentId);
            }
            entity.setMessageId(messageId);
            chatAttachmentMapper.updateById(entity);
            attachments.add(entity);
        }
        return attachments;
    }

    @Override
    public List<ChatAttachmentEntity> listByMessageIds(Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }
        return chatAttachmentMapper.selectList(new LambdaQueryWrapper<ChatAttachmentEntity>()
                .in(ChatAttachmentEntity::getMessageId, messageIds));
    }

    @Override
    public void download(String attachmentId, OutputStream out) {
        writeTo(loadOwned(attachmentId), out);
    }

    @Override
    public ChatAttachmentEntity loadByIdForPreview(String attachmentId) {
        ChatAttachmentEntity entity = chatAttachmentMapper.selectById(attachmentId);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.ATTACHMENT_NOT_FOUND, attachmentId);
        }
        return entity;
    }

    @Override
    public void writeTo(ChatAttachmentEntity entity, OutputStream out) {
        storageResolver.resolve(entity.getStorageType()).download(entity.getStoragePath(), out);
    }

    @Override
    public ChatAttachmentView toView(ChatAttachmentEntity entity) {
        return ChatAttachmentView.of(entity, previewTokenService.generatePreviewUrl(entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String attachmentId) {
        ChatAttachmentEntity entity = loadOwned(attachmentId);
        if (entity.getMessageId() != null) {
            // 已发送附件不单独删除，随会话级联清理，避免历史消息出现空洞引用
            throw new AiBusinessException(AiErrorCode.ATTACHMENT_NOT_FOUND, attachmentId);
        }
        deleteStoredFile(entity);
        chatAttachmentMapper.deleteById(attachmentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySession(String sessionId) {
        List<ChatAttachmentEntity> attachments = chatAttachmentMapper.selectList(
                new LambdaQueryWrapper<ChatAttachmentEntity>().eq(ChatAttachmentEntity::getSessionId, sessionId));
        for (ChatAttachmentEntity attachment : attachments) {
            deleteStoredFile(attachment);
        }
        chatAttachmentMapper.delete(
                new LambdaQueryWrapper<ChatAttachmentEntity>().eq(ChatAttachmentEntity::getSessionId, sessionId));
    }

    // 按行记录的 storageType 路由删除（配置变更后旧附件仍可删）；失败仅告警不阻断
    private void deleteStoredFile(ChatAttachmentEntity attachment) {
        if (StringUtils.isBlank(attachment.getStorageType()) || StringUtils.isBlank(attachment.getStoragePath())) {
            return;
        }
        try {
            storageResolver.resolve(attachment.getStorageType()).delete(attachment.getStoragePath());
        } catch (RuntimeException e) {
            log.warn(
                    "删除对话附件原文件失败(att={}, path={}): {}",
                    attachment.getId(),
                    attachment.getStoragePath(),
                    e.getMessage());
        }
    }

    private static String resolveExtension(MultipartFile file, AiProperties.Chat.Attachment cfg) {
        String fileName = StringUtils.defaultString(file.getOriginalFilename());
        String extension = StringUtils.substringAfterLast(fileName, ".").toLowerCase(Locale.ROOT);
        boolean supported =
                cfg.getImageTypes().contains(extension) || cfg.getDocTypes().contains(extension);
        if (!supported) {
            throw new AiBusinessException(AiErrorCode.ATTACHMENT_TYPE_NOT_SUPPORTED, fileName);
        }
        return extension;
    }

    private static void validateSize(MultipartFile file, AiProperties.Chat.Attachment cfg) {
        long maxBytes = cfg.getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new AiBusinessException(AiErrorCode.ATTACHMENT_SIZE_EXCEEDED, file.getOriginalFilename());
        }
    }

    private static String resolveCategory(String extension, AiProperties.Chat.Attachment cfg) {
        return cfg.getImageTypes().contains(extension) ? CATEGORY_IMAGE : CATEGORY_DOCUMENT;
    }

    private static void deleteQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("删除对话附件上传临时文件失败: {}, {}", tempFile, e.getMessage());
        }
    }
}
