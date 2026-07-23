package com.lambda.fusion.ai.subagent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.subagent.model.entity.SubAgentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SubAgentMapper extends BaseMapper<SubAgentEntity> {}
