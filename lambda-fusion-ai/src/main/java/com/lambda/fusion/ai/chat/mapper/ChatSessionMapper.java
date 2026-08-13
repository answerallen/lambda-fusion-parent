package com.lambda.fusion.ai.chat.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.chat.model.entity.ChatSessionEntity;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import com.lambda.fusion.core.utils.AuthUtils;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {

    default ChatSessionEntity selectChatSessionByIdAndUserId(String id, String userId) {
        return selectOne(new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, id)
                .eq(ChatSessionEntity::getUserId, AuthUtils.getUser().getUsername())
        );
    }

}
