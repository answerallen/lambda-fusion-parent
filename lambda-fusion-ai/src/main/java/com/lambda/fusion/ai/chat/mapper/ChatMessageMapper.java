package com.lambda.fusion.ai.chat.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.chat.model.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {}
