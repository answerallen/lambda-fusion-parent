package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.ChatSessionEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
    List<ChatSessionEntity> listByUserId(@Param("userId") Long userId);

    @Update("UPDATE ai_chat_session SET " + "message_count = message_count + #{messageIncrement}, "
            + "total_tokens = total_tokens + #{tokenIncrement}, "
            + "last_message_at = #{lastMessageAt}, "
            + "updated_at = NOW() "
            + "WHERE id = #{sessionId}")
    int atomicUpdateStatistics(
            @Param("sessionId") Long sessionId,
            @Param("messageIncrement") int messageIncrement,
            @Param("tokenIncrement") int tokenIncrement,
            @Param("lastMessageAt") LocalDateTime lastMessageAt);

    ChatSessionEntity selectByIdWithVersion(@Param("sessionId") Long sessionId);

    int updateByIdWithVersion(ChatSessionEntity session);

    @Update("UPDATE ai_chat_session SET " + "last_message_at = #{lastMessageAt}, "
            + "updated_at = NOW() "
            + "WHERE id = #{sessionId}")
    int updateLastMessageTime(@Param("sessionId") Long sessionId, @Param("lastMessageAt") LocalDateTime lastMessageAt);
}
