package com.lambda.fusion.ai.chat.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 原子会话统计更新服务
 * 提供数据库级别的原子操作，防止在并发访问时
 * 更新会话统计时出现竞态条件。
 *
 * @author Jin
 */
public interface AtomicSessionUpdateService {

    /**
     * 使用数据库级别操作原子性更新会话统计
     * 此方法通过使用SQL原子增量操作防止竞态条件
     *
     * @param sessionId 要更新的会话ID
     * @param messageIncrement 要添加的消息数量（通常为2，用户+AI消息）
     * @param tokenIncrement 要添加的token数量
     */
    void updateSessionStatistics(String sessionId, int messageIncrement, int tokenIncrement);

    /**
     * 使用数据库级别操作原子性更新会话统计（包含成本）
     * 此方法通过使用SQL原子增量操作防止竞态条件
     *
     * @param sessionId 要更新的会话ID
     * @param messageIncrement 要添加的消息数量（通常为2，用户+AI消息）
     * @param tokenIncrement 要添加的token数量
     * @param costIncrement 要添加的成本金额
     */
    void updateSessionStatistics(String sessionId, int messageIncrement, int tokenIncrement, BigDecimal costIncrement);

    /**
     * 使用乐观锁方式原子性更新会话统计
     * 此方法使用基于版本的乐观锁和重试逻辑
     *
     * @param sessionId 要更新的会话ID
     * @param messageIncrement 要添加的消息数量
     * @param tokenIncrement 要添加的token数量
     */
    void updateSessionStatisticsOptimistic(String sessionId, int messageIncrement, int tokenIncrement);

    /**
     * 使用乐观锁方式原子性更新会话统计（包含成本）
     * 此方法使用基于版本的乐观锁和重试逻辑
     *
     * @param sessionId 要更新的会话ID
     * @param messageIncrement 要添加的消息数量
     * @param tokenIncrement 要添加的token数量
     * @param costIncrement 要添加的成本金额
     */
    void updateSessionStatisticsOptimistic(
            String sessionId, int messageIncrement, int tokenIncrement, BigDecimal costIncrement);

    /**
     * 仅原子性更新最后消息时间戳
     * 当只需要更新时间戳而不改变计数器时使用
     *
     * @param sessionId 要更新的会话ID
     * @param lastMessageAt 最后消息的时间戳
     */
    void updateLastMessageTime(String sessionId, LocalDateTime lastMessageAt);
}
