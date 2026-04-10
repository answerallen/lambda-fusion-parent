package com.lambda.fusion.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.ai.AiConstants.Enums.SessionStatus;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.mapper.RobotMapper;
import com.lambda.fusion.ai.model.ChatSession;
import com.lambda.fusion.ai.model.CreateSession;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.model.entity.RobotEntity;
import com.lambda.fusion.ai.service.ChatSessionService;
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
    private final RobotMapper robotMapper;

    @Override
    public ChatSession createSession(CreateSession createSession) {
        ChatSessionEntity entity = createSession.toEntity();

        entity.setUserId(AuthUtils.getUser().getName());
        entity.setTenantId(AuthUtils.getTenantId());

        // 挂载机器人参数
        if (createSession.getRobotId() != null) {
            RobotEntity robot = robotMapper.selectById(createSession.getRobotId());
            if (robot == null) {
                throw AiBusinessException.robotNotFound(createSession.getRobotId());
            }
            if (robot.getLlmModelId() != null) entity.setLlmModelId(robot.getLlmModelId());
            if (robot.getSystemPrompt() != null) entity.setSystemPrompt(robot.getSystemPrompt());
            if (robot.getKbId() != null) entity.setKbId(robot.getKbId());
            if (robot.getWorkflowId() != null) entity.setWorkflowId(robot.getWorkflowId());
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
        return ConvertUtils.convert(entity);
    }
}
