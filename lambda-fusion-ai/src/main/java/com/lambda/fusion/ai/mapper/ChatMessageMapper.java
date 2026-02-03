package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
    List<ChatMessageEntity> listBySessionId(@Param("sessionId") Long sessionId, @Param("limit") Integer limit);
}
