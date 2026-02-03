package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.entity.ChatMessageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
    List<ChatMessageEntity> listBySessionId(@Param("sessionId") Long sessionId, @Param("limit") Integer limit);
}
