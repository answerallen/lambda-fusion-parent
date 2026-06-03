package com.lambda.fusion.ai.service.impl;

import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.ChatSessionMapper;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.service.AtomicSessionUpdateService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
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
        updateSessionStatistics(sessionId, messageIncrement, tokenIncrement, BigDecimal.ZERO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionStatistics(
            String sessionId, int messageIncrement, int tokenIncrement, BigDecimal costIncrement) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "会话ID不能为空");
        }

        if (costIncrement == null) {
            costIncrement = BigDecimal.ZERO;
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows;

        if (costIncrement.compareTo(BigDecimal.ZERO) > 0) {
            updatedRows = chatSessionMapper.atomicUpdateStatisticsWithCost(
                    sessionId, messageIncrement, tokenIncrement, costIncrement, now);
        } else {
            updatedRows = chatSessionMapper.atomicUpdateStatistics(sessionId, messageIncrement, tokenIncrement, now);
        }

        if (updatedRows == 0) {
            log.warn("更新会话统计失败，会话ID: {}，会话可能不存在", sessionId);
            throw AiBusinessException.sessionNotFound(sessionId);
        }

        log.debug(
                "成功更新会话{}统计: 消息 +{}, token +{}, cost +{}", sessionId, messageIncrement, tokenIncrement, costIncrement);
    }

    @Override
    public void updateSessionStatisticsOptimistic(String sessionId, int messageIncrement, int tokenIncrement) {
        updateSessionStatisticsOptimistic(sessionId, messageIncrement, tokenIncrement, BigDecimal.ZERO);
    }

    @Override
    public void updateSessionStatisticsOptimistic(
            String sessionId, int messageIncrement, int tokenIncrement, BigDecimal costIncrement) {
        if (sessionId == null) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "会话ID不能为空");
        }

        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            int finalAttempt = attempt;
            if (costIncrement == null) {
                costIncrement = BigDecimal.ZERO;
            }
            BigDecimal finalCostIncrement = costIncrement;

            Boolean success = transactionTemplate.execute(status -> {
                try {
                    ChatSessionEntity session = chatSessionMapper.selectByIdWithVersion(sessionId);
                    if (session == null) {
                        throw AiBusinessException.sessionNotFound(sessionId);
                    }

                    session.setMessageCount(session.getMessageCount() + messageIncrement);
                    session.setTotalTokens(session.getTotalTokens() + tokenIncrement);
                    if (session.getTotalCost() == null) {
                        session.setTotalCost(BigDecimal.ZERO);
                    }
                    session.setTotalCost(session.getTotalCost().add(finalCostIncrement));
                    session.setLastMessageAt(LocalDateTime.now());

                    int updatedRows = chatSessionMapper.updateByIdWithVersion(session);
                    if (updatedRows > 0) {
                        log.debug(
                                "使用乐观锁成功更新会话{}统计: 消息 +{}, token +{}, cost +{}",
                                sessionId,
                                messageIncrement,
                                tokenIncrement,
                                finalCostIncrement);
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
