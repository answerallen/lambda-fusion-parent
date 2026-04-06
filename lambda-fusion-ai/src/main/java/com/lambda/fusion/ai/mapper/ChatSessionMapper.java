package com.lambda.fusion.ai.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
@DS("@aiProperties.dataSource.name")
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {

    /**
     * 按用户ID查询会话列表
     */
    List<ChatSessionEntity> listByUserId(@Param("userId") String userId);

    /**
     * 原子性更新会话统计
     */
    @Update("UPDATE ai_chat_session SET " + "message_count = message_count + #{messageIncrement}, "
            + "total_tokens = total_tokens + #{tokenIncrement}, "
            + "last_message_at = #{lastMessageAt}, "
            + "updated_at = NOW() "
            + "WHERE id = #{sessionId}")
    int atomicUpdateStatistics(
            @Param("sessionId") String sessionId,
            @Param("messageIncrement") int messageIncrement,
            @Param("tokenIncrement") int tokenIncrement,
            @Param("lastMessageAt") LocalDateTime lastMessageAt);

    /**
     * 按ID和版本查询(乐观锁)
     */
    ChatSessionEntity selectByIdWithVersion(@Param("sessionId") String sessionId);

    /**
     * 按ID和版本更新(乐观锁)
     */
    int updateByIdWithVersion(ChatSessionEntity session);

    /**
     * 更新最后消息时间
     */
    @Update("UPDATE ai_chat_session SET " + "last_message_at = #{lastMessageAt}, "
            + "updated_at = NOW() "
            + "WHERE id = #{sessionId}")
    int updateLastMessageTime(
            @Param("sessionId") String sessionId, @Param("lastMessageAt") LocalDateTime lastMessageAt);

    /**
     * 按用户ID和状态查询会话
     * @param userId 用户ID
     * @param status 会话状态(ACTIVE/ARCHIVED)
     * @return 会话列表
     */
    List<ChatSessionEntity> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    /**
     * 按租户ID查询会话
     * @param tenantId 租户ID
     * @param status 会话状态
     * @return 会话列表
     */
    List<ChatSessionEntity> selectByTenantId(@Param("tenantId") Long tenantId, @Param("status") String status);

    /**
     * 查询过期会话(超过30天未活动)
     * @return 过期会话列表
     */
    List<ChatSessionEntity> selectExpiredSessions();

    /**
     * 批量更新会话状态
     * @param sessionIds 会话ID列表
     * @param status 新状态
     * @return 更新数量
     */
    int updateStatusBatch(@Param("sessionIds") List<Long> sessionIds, @Param("status") String status);

    /**
     * 批量删除用户会话
     * @param userIds 用户ID列表
     * @return 删除数量
     */
    int deleteByUserIdBatch(@Param("userIds") List<Long> userIds);

    /**
     * 统计用户活跃会话数
     * @param userId 用户ID
     * @return 活跃会话数
     */
    Integer countActiveSessionsByUserId(@Param("userId") Long userId);
}
