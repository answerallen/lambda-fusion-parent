package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.entity.ChatSessionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
    List<ChatSessionEntity> listByUserId(@Param("userId") Long userId);
}
