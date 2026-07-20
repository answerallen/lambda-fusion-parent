package com.lambda.fusion.ai.chat.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.ai.AiConstants.Enums.SessionStatus;
import com.lambda.fusion.ai.apps.mapper.AppsMapper;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.chat.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.chat.model.ChatSession;
import com.lambda.fusion.ai.chat.model.CreateSession;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.chat.service.ChatSessionService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.core.utils.AuthUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSessionEntity>
        implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final AppsMapper appsMapper;

    @Override
    public ChatSession createSession(CreateSession createSession) {
        ChatSessionEntity entity = createSession.toEntity();

        entity.setUserId(AuthUtils.getUser().getName());
        entity.setTenantId(AuthUtils.getTenantId());

        // 挂载机器人参数
        if (createSession.getAppId() != null) {
            AppEntity app = appsMapper.selectById(createSession.getAppId());
            if (app == null) {
                throw AiBusinessException.appNotFound(createSession.getAppId());
            }
            if (app.getLlmModelId() != null) entity.setLlmModelId(app.getLlmModelId());
            if (app.getSystemPrompt() != null) entity.setSystemPrompt(app.getSystemPrompt());
            if (app.getKbIds() != null) entity.setKbIds(app.getKbIds());
            // 执行参数全量快照（快照即稳定性：app 后续编辑不影响在途会话）
            // 旧实现仅拷贝 4 项，temperature/maxTokens 虽在 session 却未拷贝、retrievalTopK/similarityThreshold 不在 session
            if (app.getTemperature() != null) entity.setTemperature(app.getTemperature());
            if (app.getMaxTokens() != null) entity.setMaxTokens(app.getMaxTokens());
            if (app.getRetrievalTopK() != null) entity.setRetrievalTopK(app.getRetrievalTopK());
            if (app.getSimilarityThreshold() != null) entity.setSimilarityThreshold(app.getSimilarityThreshold());
        }
        entity.setMessageCount(0);
        entity.setTotalTokens(0);
        entity.setTotalCost(BigDecimal.ZERO);
        entity.setStatus(SessionStatus.ACTIVE.name());
        chatSessionMapper.insert(entity);
        return entityToVO(entity);
    }

    @Override
    public List<ChatSession> listUserSessions(String userId) {
        return chatSessionMapper.listByUserId(userId).stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    public void archiveSession(String sessionId) {
        // 验证输入参数
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.SESSION_NOT_FOUND, "会话ID不能为空");
        }

        ChatSessionEntity entity = chatSessionMapper.selectById(sessionId);
        if (entity == null) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }
        validateSessionAccess(entity, sessionId);

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

    private void validateSessionAccess(ChatSessionEntity session, String sessionId) {
        String currentTenantId = AuthUtils.getTenantId();
        String currentUserId = AuthUtils.getUser() != null ? AuthUtils.getUser().getName() : null;
        if (currentTenantId != null
                && session.getTenantId() != null
                && !currentTenantId.equals(session.getTenantId())) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }
        if (currentUserId != null && session.getUserId() != null && !currentUserId.equals(session.getUserId())) {
            throw AiBusinessException.sessionNotFound(sessionId);
        }
    }

    private ChatSession entityToVO(ChatSessionEntity entity) {
        return ConvertUtils.convert(entity);
    }
}
