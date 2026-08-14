package com.lambda.fusion.ai.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {

    default ChatSessionEntity selectOwned(String id, String userId) {
        return selectOwned(id, userId, false);
    }

    default ChatSessionEntity selectOwnedForUpdate(String id, String userId) {
        return selectOwned(id, userId, true);
    }

    default ChatSessionEntity selectForUpdate(String id) {
        return selectOne(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, id)
                .last("FOR UPDATE"));
    }

    private ChatSessionEntity selectOwned(String id, String userId, boolean forUpdate) {
        LambdaQueryWrapper<ChatSessionEntity> query = new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, id)
                .eq(ChatSessionEntity::getUserId, userId);
        if (forUpdate) {
            query.last("FOR UPDATE");
        }
        return selectOne(query);
    }
}
