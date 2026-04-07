package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.ChatMessageEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

    /**
     * 按会话ID查询消息列表
     * <p>
     * <strong>返回顺序</strong>：按创建时间倒序（最新消息在前）。
     * 调用方需要根据业务需求决定是否反转顺序。
     *
     * @param sessionId 会话ID
     * @param limit 限制数量（取最近的N条消息）
     * @return 消息列表，按 created_at DESC 排序
     */
    List<ChatMessageEntity> listBySessionId(@Param("sessionId") String sessionId, @Param("limit") Integer limit);

    /**
     * 按会话ID和角色查询消息
     * @param sessionId 会话ID
     * @param role 角色(user/assistant)
     * @param limit 限制数量
     * @return 消息列表
     */
    List<ChatMessageEntity> selectBySessionIdAndRole(
            @Param("sessionId") String sessionId, @Param("role") String role, @Param("limit") Integer limit);

    /**
     * 统计会话消息数
     * @param sessionId 会话ID
     * @return 消息数量
     */
    Integer countBySessionId(@Param("sessionId") String sessionId);

    /**
     * 删除会话所有消息
     * @param sessionId 会话ID
     * @return 删除数量
     */
    int deleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * 批量更新反馈
     * @param feedbackMap key为messageId, value为feedback值
     * @return 更新数量
     */
    int updateFeedbackBatch(@Param("feedbackMap") Map<Long, Integer> feedbackMap);
}
