package com.lambda.fusion.ai.mcp.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.mcp.model.entity.McpServerEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface McpServerMapper extends BaseMapper<McpServerEntity> {}
