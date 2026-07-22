package com.lambda.fusion.ai.channel.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.channel.model.entity.ChannelConfigEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface ChannelConfigMapper extends BaseMapper<ChannelConfigEntity> {}
