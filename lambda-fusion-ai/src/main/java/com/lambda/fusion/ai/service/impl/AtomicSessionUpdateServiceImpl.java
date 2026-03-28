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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionStatistics(Long sessionId, int messageIncrement, int tokenIncrement) {
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
    public void updateSessionStatisticsOptimistic(Long sessionId, int messageIncrement, int tokenIncrement) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "会话ID不能为空");
        }

        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // 获取当前会话及版本
                ChatSessionEntity session = chatSessionMapper.selectByIdWithVersion(sessionId);
                if (session == null) {
                    throw AiBusinessException.sessionNotFound(sessionId);
                }

                // 更新统计
                session.setMessageCount(session.getMessageCount() + messageIncrement);
                session.setTotalTokens(session.getTotalTokens() + tokenIncrement);
                session.setLastMessageAt(LocalDateTime.now());

                // 尝试乐观更新
                int updatedRows = chatSessionMapper.updateByIdWithVersion(session);
                if (updatedRows > 0) {
                    log.debug("使用乐观锁成功更新会话{}统计: 消息 +{}, token +{}", sessionId, messageIncrement, tokenIncrement);
                    return; // 成功
                }

                // 发生版本冲突，将重试
                log.debug("会话{}乐观锁冲突，尝试 {}/{}", sessionId, attempt + 1, maxRetries);

            } catch (OptimisticLockingFailureException e) {
                log.debug("会话{}乐观锁失败，尝试 {}/{}", sessionId, attempt + 1, maxRetries);
            }

            // 使用指数退避重试（除了最后一次尝试）
            // 注意：不在 @Transactional 方法中使用 Thread.sleep()，改为异步处理
            if (attempt < maxRetries - 1) {
                long backoffMs = (long) Math.pow(2, attempt) * 100; // 100ms, 200ms, 400ms
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AiBusinessException(AiErrorCode.SYSTEM_ERROR, "重试期间线程被中断", ie);
                }
            }
        }

        // 所有重试都已用尽
        log.error("由于并发修改，在{}次尝试后更新会话{}统计失败", maxRetries, sessionId);
        throw new AiBusinessException(AiErrorCode.CONCURRENT_UPDATE_FAILED, "由于并发修改导致更新会话统计失败");
    }

    /**
     * 异步更新会话统计（推荐用于高并发场景）
     * 避免在事务中使用 Thread.sleep()
     */
    @Async
    public CompletableFuture<Void> updateSessionStatisticsAsync(
            Long sessionId, int messageIncrement, int tokenIncrement) {
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
    public void updateLastMessageTime(Long sessionId, LocalDateTime lastMessageAt) {
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
