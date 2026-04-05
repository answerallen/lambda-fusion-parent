package com.lambda.fusion.ai.service.impl;

import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.service.AtomicSessionUpdateService;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 原子会话统计更新服务实现
 * 提供数据库级别的原子操作，防止在并发访问时
 * 更新会话统计时出现竞态条件。
 *
 * @author Jin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtomicSessionUpdateServiceImpl implements AtomicSessionUpdateService {

    private final ChatSessionMapper chatSessionMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionStatistics(String sessionId, int messageIncrement, int tokenIncrement) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "会话ID不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows = chatSessionMapper.atomicUpdateStatistics(sessionId, messageIncrement, tokenIncrement, now);

        if (updatedRows == 0) {
            log.warn("更新会话统计失败，会话ID: {}，会话可能不存在", sessionId);
            throw AiBusinessException.sessionNotFound(sessionId);
        }

        log.debug("成功更新会话{}统计: 消息 +{}, token +{}", sessionId, messageIncrement, tokenIncrement);
    }

    @Override
    public void updateSessionStatisticsOptimistic(String sessionId, int messageIncrement, int tokenIncrement) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "会话ID不能为空");
        }

        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            int finalAttempt = attempt;
            Boolean success = transactionTemplate.execute(status -> {
                try {
                    ChatSessionEntity session = chatSessionMapper.selectByIdWithVersion(sessionId);
                    if (session == null) {
                        throw AiBusinessException.sessionNotFound(sessionId);
                    }

                    session.setMessageCount(session.getMessageCount() + messageIncrement);
                    session.setTotalTokens(session.getTotalTokens() + tokenIncrement);
                    session.setLastMessageAt(LocalDateTime.now());

                    int updatedRows = chatSessionMapper.updateByIdWithVersion(session);
                    if (updatedRows > 0) {
                        log.debug("使用乐观锁成功更新会话{}统计: 消息 +{}, token +{}", sessionId, messageIncrement, tokenIncrement);
                        return true;
                    }

                    log.debug("会话{}乐观锁冲突，尝试 {}/{}", sessionId, finalAttempt + 1, maxRetries);
                    return false;

                } catch (OptimisticLockingFailureException e) {
                    log.debug("会话{}乐观锁失败，尝试 {}/{}", sessionId, finalAttempt + 1, maxRetries);
                    return false;
                }
            });

            if (Boolean.TRUE.equals(success)) {
                return;
            }

            if (attempt < maxRetries - 1) {
                long backoffMs = (long) Math.pow(2, attempt) * 100;
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "重试期间线程被中断", ie);
                }
            }
        }

        log.error("由于并发修改，在{}次尝试后更新会话{}统计失败", maxRetries, sessionId);
        throw new AiBusinessException(AiErrorCode.CONCURRENT_UPDATE_FAILED, "由于并发修改导致更新会话统计失败");
    }

    @Async
    public CompletableFuture<Void> updateSessionStatisticsAsync(
            String sessionId, int messageIncrement, int tokenIncrement) {
        return CompletableFuture.runAsync(() -> {
            try {
                updateSessionStatisticsOptimistic(sessionId, messageIncrement, tokenIncrement);
            } catch (Exception e) {
                log.error("异步更新会话统计失败，会话ID: {}", sessionId, e);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLastMessageTime(String sessionId, LocalDateTime lastMessageAt) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "会话ID不能为空");
        }
        if (lastMessageAt == null) {
            lastMessageAt = LocalDateTime.now();
        }

        int updatedRows = chatSessionMapper.updateLastMessageTime(sessionId, lastMessageAt);

        if (updatedRows == 0) {
            log.warn("更新会话最后消息时间失败，会话ID: {}，会话可能不存在", sessionId);
            throw AiBusinessException.sessionNotFound(sessionId);
        }

        log.debug("成功更新会话{}最后消息时间为{}", sessionId, lastMessageAt);
    }
}
